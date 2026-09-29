# convention-check 평가(evals)

`SKILL.md` · `review-points.md` 를 고친 뒤 결과가 나빠지지 않았는지 확인하는 회귀 평가다.

```bash
.claude/skills/convention-check/evals/run.sh                      # 전체 케이스
.claude/skills/convention-check/evals/run.sh 03-comment-only      # 한 케이스
```

- 케이스마다 `change.patch` 를 적용하고, 헤드리스 `claude -p` 에 **Skill 이름 없이** 평소 말투로 점검을 부탁한다
  (자동 호출까지 함께 시험한다). 끝나면 패치를 되돌린다.
- `modern/` 에 커밋하지 않은 변경이 있으면 시작하지 않는다(내 작업을 덮지 않기 위해).
- 점검 중 패치한 파일이 바뀌면 FAIL — "코드를 고치지 않는다" 규칙 검사.
- 출력 전문은 `results/<케이스>.md` 에 남는다(`.gitignore` 로 커밋 제외).

| 케이스 | 보는 것 |
|---|---|
| `01-controller-jdbc` | 새 파일(`??`) 점검, 위반 항목 1 · 2 · 3 · 6 과 줄번호, 확인 필요 R1 · R2 |
| `02-service-n-plus-one` | 기존 파일 수정(diff) 점검, 항목 7, 확인 필요 R3 · R5, 지킨 항목을 위반으로 잡지 않는지 |
| `03-comment-only` | 오탐 검사 — 주석만 바뀐 변경에서 위반 · 확인 필요가 없어야 한다 |

## 케이스 추가

1. `modern/` 에서 시험할 변경을 만든다. 새 파일이면 `git add -N <파일>` 로 diff 에 보이게 한다.
2. `git diff -- <파일> > cases/<번호-이름>/change.patch` 후 변경을 되돌린다(`git add -N` 했으면 `git rm --cached`).
3. `cases/<번호-이름>/expect.txt` 에 한 줄에 하나씩 `MUST` / `MUST_NOT` + 확장 정규식 + `  # 설명` 을 적는다.
   줄번호는 결과가 `파일.java:23, 25` · `36-37` 처럼 나와도 맞도록 기존 케이스의 정규식을 본떠 쓴다.

## 채점의 한계

- 정규식 채점이라 표현이 조금 달라도 통과 · 실패가 바뀔 수 있다. FAIL 이 나면 `results/` 전문을 먼저 읽는다.
- 모델 출력은 매번 조금씩 다르다. 한 번의 FAIL 보다 여러 번 돌렸을 때의 경향을 본다.
- 기존 코드가 바뀌어 `git apply` 가 실패하면 그 케이스의 패치를 다시 만든다.
