#!/usr/bin/env node
// hooks/ask-risky.mjs
// PreToolUse(Bash) 훅: 가끔은 필요하지만 되돌릴 수 없는 명령을 막지 않고 사람에게 확인받는다.
// 방식 B = exit 0 + stdout JSON(permissionDecision: "ask"). stdout 에는 이 JSON 말고 아무것도 내지 않는다.
import { readFileSync } from "node:fs";

let input;
try {
  input = JSON.parse(readFileSync(0, "utf8")); // fd 0 = stdin
} catch {
  // stdin JSON을 읽지 못하면 판단하지 않고 통과(fail-open)시킨다.
  process.exit(0);
}

if (input.tool_name !== "Bash") process.exit(0);

const command = String(input.tool_input?.command ?? "");

const RULES = [
  { pattern: /\bgit\s+clean\b(?=[^;&|]*\s-[a-zA-Z]*f)/, reason: "git clean -f 는 추적하지 않는 파일을 되돌릴 수 없게 지웁니다. 정말 실행할까요?" },
  { pattern: /\bgit\s+checkout\s+(--\s+)?\.(\s|$)/, reason: "git checkout . 은 작업 중인 변경을 모두 버립니다. 정말 실행할까요?" },
  { pattern: /\bgit\s+stash\s+(drop|clear)\b/, reason: "git stash drop · clear 는 보관한 변경을 지웁니다. 정말 실행할까요?" },
];

for (const { pattern, reason } of RULES) {
  if (pattern.test(command)) {
    process.stdout.write(
      JSON.stringify({
        hookSpecificOutput: {
          hookEventName: "PreToolUse",
          permissionDecision: "ask",
          permissionDecisionReason: reason,
        },
      })
    );
    process.exit(0);
  }
}

process.exit(0);
