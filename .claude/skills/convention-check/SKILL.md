---
name: convention-check
description: 변경된 코드가 CLAUDE.md 의 코딩 컨벤션 · 금지 사항을 지켰는지 점검하고 위반을 파일:줄번호 표로 보고합니다. "컨벤션 점검", "커밋 전 점검", "리뷰 전에 확인", "팀 규칙 지켰는지 봐 줘" 같은 요청에 사용합니다.
argument-hint: "[점검할 경로 — 생략하면 git status · git diff 의 변경 파일]"
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash(git diff *)
  - Bash(git status *)
---

# 컨벤션 점검

## 점검 대상
- 인자가 있으면 `$ARGUMENTS` 경로 아래의 파일.
- 없으면 `git status --short` 와 `git diff` 에 잡힌 변경 파일. `??` 로 보이는 아직 add 하지 않은 새 파일도 포함한다.
- 새 파일은 전체를, 수정 파일은 diff 로 바뀐 줄과 그 주변을 읽는다.
- 이 Skill 의 `evals/` 폴더는 채점용 정답이 든 평가 자료다. 점검 대상에 넣지 않고 읽지도 않는다.

## 점검 항목 — "이게 보이면 위반"
1. `*Controller.java` 의 생성자 파라미터에 `*Service` 가 아닌 타입이 있다(`*Repository`, `DataSource`, `JdbcTemplate`,
   `EntityManager` 등). 또는 SQL 문자열(select · insert · update · delete 리터럴), `Connection` ·
   `PreparedStatement` 사용, `try`/`catch` 중 하나라도 있다.
2. `catch` 블록 본문이 비었거나 주석만 있다(로그도, 다시 던지기도 없음).
3. `System.out` · `System.err` · `printStackTrace()` 가 있다. web 코드는 `console.log`.
4. `src/main` 에 `@Autowired` 가 있거나 와일드카드 import(`.*;`)가 있다.
5. `*Service.java` 안에 인자 없는 `now()` 호출(`LocalDateTime.now()`, `Instant.now()` 등)이 있다.
6. 컨트롤러 메서드의 반환 타입이 `record` 나 그 `List` 가 아니다(`@Entity`, `Map`, `Object`, `String`,
   record 가 아닌 class 등). `ResponseEntity<…>` 로 감쌌으면 안쪽 타입으로 판정한다. 이름이 `*Response` 가 아닌
   record(예: `ClassReport`)는 통과, 헬스체크 `common/RootController` 의 `Map` 은 기존 예외로 통과.
7. 새 엔드포인트(`@GetMapping` · `@PostMapping` 등)나 새 public 서비스 메서드를 부르는 테스트가 `src/test` 에 없다.
8. 변경 파일에 `build.gradle` 의 `dependencies`, `package.json`, `package-lock.json`, `db/`, `application.yml`,
   `legacy/`, `characterization/` 가 섞여 있다.

## 확인 필요 점검
- 항목 1~8 을 끝낸 뒤 같은 폴더의 `review-points.md` 를 읽고, 변경된 줄에 R1~R9 의 신호가 보일 때만 올린다.
- 위반과 섞지 않는다. 단정하지 않고 예 / 아니오로 답할 질문으로 쓴다. 최대 5건.
- 질문 맨 앞에 해당 번호를 괄호로 붙인다. 예: `(R1) 이 조회는 공개 문항만 내보내야 합니까?`

## 판정 원칙
- 위반은 규칙 문구와 코드 줄이 1:1 로 맞을 때만. 한 줄에 여러 규칙이 걸리면 규칙마다 한 행.
- 같은 원인에서 나온 지적은 한 행으로 묶되, 관련 줄번호는 모두 첫 열에 적는다(예: `X.java:36-37, 50-51`).
- 줄번호는 파일을 Read 로 연 결과에서 옮긴다. 추측한 줄번호를 쓰지 않는다.

## 출력 형식 (이 순서로 고정)
1. **판정 요약** — 점검한 파일 수, 위반 건수, 확인 필요 건수, 항목 1~8 별 통과 / 위반 / 해당 없음 한 줄씩.
2. **위반 목록 표** — 없으면 "위반 없음" 한 줄.

   | 파일:줄번호 | 어긴 규칙 | 수정 방향 |
   |---|---|---|

3. **확인 필요 표** — 없으면 생략.

   | 파일:줄번호 | 확인할 질문 | 근거 |
   |---|---|---|

## 하지 말 것
- 코드를 직접 고치지 않는다. 보고만 한다.
- 근거 라인을 댈 수 없는 지적은 하지 않고 "확인 필요"로 남긴다.
- 검증 명령(`./gradlew test` 등)은 실행하지 않는다. 답변 끝에 "검증 명령: 실행하지 않음" 을 적는다.
