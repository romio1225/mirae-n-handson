#!/usr/bin/env bash
# convention-check Skill 회귀 평가.
# 케이스마다 change.patch 를 적용 → 헤드리스 claude 에 평소 말투로 점검 요청 → expect.txt 로 채점 → 패치 되돌림.
# 사용: .claude/skills/convention-check/evals/run.sh [케이스 폴더 이름...]   (생략하면 전체)
set -euo pipefail

ROOT=$(git rev-parse --show-toplevel)
cd "$ROOT"
EVALS=.claude/skills/convention-check/evals
PROMPT=${EVAL_PROMPT:-'커밋하기 전에 방금 추가된 변경이 우리 팀 컨벤션을 지켰는지 점검해 주세요.'}

if [ -n "$(git status --porcelain -- modern/)" ]; then
    echo "modern/ 에 커밋하지 않은 변경이 있어 중단합니다. 먼저 커밋하거나 치워 주세요." >&2
    exit 2
fi

if [ $# -gt 0 ]; then cases=("$@"); else cases=($(ls "$EVALS/cases")); fi
mkdir -p "$EVALS/results"

applied=""
revert() { if [ -n "$applied" ]; then git apply -R "$applied" && applied=""; fi; }
trap revert EXIT

fingerprint() { git apply --numstat "$1" | cut -f3 | xargs cat | shasum; }

failed=0
for c in "${cases[@]}"; do
    dir="$EVALS/cases/$c"
    out="$EVALS/results/$c.md"
    echo "== $c"

    git apply "$dir/change.patch"
    applied="$dir/change.patch"
    before=$(fingerprint "$applied")

    claude -p "$PROMPT" \
        --allowedTools Skill Read Grep Glob "Bash(git diff *)" "Bash(git status *)" \
        --disallowedTools Edit Write NotebookEdit \
        > "$out" 2>&1 || echo "   (claude 종료 코드 $?)"

    case_fail=0
    if [ "$(fingerprint "$applied")" != "$before" ]; then
        echo "   FAIL  점검 중 코드가 바뀜 — Skill 은 코드를 고치면 안 된다"
        case_fail=1
    fi
    revert

    while IFS= read -r line; do
        case "$line" in ''|'#'*) continue ;; esac
        kind=${line%% *}
        rest=${line#* }
        re=${rest%%  #*}
        desc=${rest#*  # }
        if grep -Eq -- "$re" "$out"; then hit=1; else hit=0; fi
        if { [ "$kind" = MUST ] && [ $hit = 1 ]; } || { [ "$kind" = MUST_NOT ] && [ $hit = 0 ]; }; then
            echo "   pass  $desc"
        else
            echo "   FAIL  $kind $desc"
            case_fail=1
        fi
    done < "$dir/expect.txt"

    [ $case_fail = 0 ] || failed=$((failed + 1))
    echo "   결과 전문: $out"
done

echo "== 실패한 케이스 ${failed} / ${#cases[@]}"
[ $failed = 0 ]
