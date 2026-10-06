# 마이그레이션 검증 리포트 1

- 검증 대상 범위: `upstream/main...HEAD` (6개 커밋, 57개 파일, +7707 / -5)
- 요청 문장: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.
- 리뷰 모델: Sonnet (리뷰 세션, 코드 수정 없음)
- 판정 대상으로 본 이관: grade `GET /report?class_id=` → `GET /api/grades/report?class_id=` (`usp_class_report`)

## 실행한 테스트

| 명령 | 결과 |
|---|---|
| `cd modern/api && ./gradlew cleanTest test` | 통과 39 / 실패 0 / 오류 0 / 건너뜀 0 |
| `cd modern/web && npm run lint` · `npm run typecheck` · `npm test` | lint 통과 · typecheck 통과 · 테스트 19 통과 / 0 실패 |
| `cd characterization && npx vitest run tests/grade.test.js` (레거시 8083) | 16 통과 / 0 실패 |
| `cd characterization && TARGET_BASE_URL=http://localhost:8080 npx vitest run tests/grade.test.js` (새 API) | 16 통과 / 0 실패 |
| `cd characterization && npm test` (레거시 기본 주소, 전체) | 29 통과 / 29 실패 — 실패는 모두 `item-bank.test.js`(28) · `example-units.test.js`(1), 원인 `ECONNREFUSED :8081`(문항 은행 레거시 미기동) |
| `cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test` (전체) | 29 통과 / 28 실패 / 1 건너뜀 — 실패는 모두 `item-bank.test.js` 28건(새 API 에 해당 엔드포인트 이관 없음 · 대상 서버 미기동) |

grade 이관 대상 테스트는 양쪽 모두 전부 통과했다. 전체 `npm test` 의 실패는 이번 이관과 무관한 item-bank 모듈이다.

## 판정: 반려

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 충족(조건부) | api 39/0, web 19/0(lint·typecheck 통과), grade 동작 보존 16/0(레거시·새 API 둘 다). 전체 `characterization npm test` 는 item-bank 서버 미기동으로 실패 → #6 |
| 치명 이슈 0건 | 충족 | 치명 이슈 없음(리터럴 비밀번호 없음, SQL 은 `:classId` 바인딩, 쓰기·삭제 코드 없음) |
| 요청 범위 이탈 없음 | 미충족 | #1, #2, #3. 요청은 "엔드포인트 1개 이관"인데 범위에 문서 11 · Skill 7 · item-bank 동작 보존 · 웹 화면 14 · 시드 · `CLAUDE.md` 가 섞여 있음 |
| 컨벤션 준수 | 충족(경고 1) | #4 (`@Transactional(readOnly = true)` 규칙 문서화된 예외). 그 외 위반 없음 |

기준 3(범위 이탈) 하나가 미충족이라 반려한다.

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `modern/web/src/App.tsx:1-27`, `modern/web/src/components/GradeReportView.tsx:1`, `ModuleTabs.tsx:1`, `ClassPicker.tsx:1`, `modern/web/src/styles.css`(+297줄) | 요청은 엔드포인트 이관인데 성적 현황 화면 · 탭 · 학급 선택 · 대규모 CSS 와 기존 헤더(`<h1>문항 은행</h1>`) 개편이 함께 들어옴 | 웹 화면은 별도 요청 · 별도 PR 로 분리하거나 요청에 포함됐는지 사람이 확정 |
| 2 | 경고 | `modern/api/build.gradle:27-28`, `modern/api/src/main/resources/application.yml:28-37`, `db/mssql/init/02-seed.sql:224-257` | `CLAUDE.md` 가 사람에게 먼저 묻도록 한 파일(`build.gradle` dependencies · `application.yml` 접속 설정 · `db/` 시드)이 바뀜. 승인 근거가 diff 에서 확인되지 않음. 시드 C4 추가는 C1~C3 결과를 바꾸지 않는다고 주석에 있으나 레거시 기준 데이터 변경임 | 변경 승인 여부를 사람이 확인하고, 이유와 대안을 PR 설명에 남김 |
| 3 | 경고 | `docs/**`(11개), `.claude/skills/**`(7개), `CLAUDE.md`, `characterization/tests/item-bank.test.js`, `characterization/__snapshots__/item-bank.test.js.snap`(2721줄) | 이 이관 요청으로 설명되지 않는 변경(문서화 · Skill · item-bank 베이스라인). 한 범위에 여러 요청이 섞여 있음 | 커밋 범위를 좁혀(예: `4b53884`·`f0c6e7c`·`265a253`) 다시 검증하거나 요청별로 PR 분리 |
| 4 | 경고 | `modern/api/src/main/java/com/example/grade/GradeReportService.java:29-30` (설명 주석 `:23-24`) | `CLAUDE.md` 는 조회 서비스에 `@Transactional(readOnly = true)` 를 요구하나 의도적으로 생략(MS-SQL 만 읽는데 트랜잭션 관리자는 MariaDB 풀을 잡음). 사유는 타당하나 규칙과 문자 그대로는 어긋남 | `CLAUDE.md` 에 예외를 명시하거나 MS-SQL 용 트랜잭션 관리자를 지정해 규칙을 만족시킴. 사람이 결정 |
| 5 | 제안 | `modern/api/src/main/java/com/example/grade/GradeReportRepository.java:28-40`, `GradeReportRepositoryTest.java:35-43` | SQL 3개를 별도 호출로 읽어 일관된 스냅샷이 아니고, 리포지토리 쿼리에 대한 실제 MS-SQL 대상 테스트가 없음(동작은 characterization 이 보장) | 필요하면 `@DataJpaTest` 대신 Testcontainers 등 별도 검토. 현재는 characterization 으로 대체 가능 |
| 6 | 제안 | (전체 `characterization npm test`) | 완료 기준은 `characterization/` 의 `npm test` 전체 통과인데 item-bank 서버(8081)가 꺼져 있어 전체 실행은 실패. grade 만 통과 확인 | 8081 기동 후 전체 재실행, 또는 이번 요청의 완료 기준을 grade 모듈로 한정한다고 확정 |
| 7 | 제안 | `modern/api/src/main/resources/application.yml:33` | JDBC URL 에 `trustServerCertificate=true` · 로컬 접속 고정. 개발용 기본값이라 문제는 아니나 운영 프로파일 분리 필요 | 운영 값은 프로파일/환경 변수로 분리 |

## 확인 필요

| 파일 또는 범위 | 확인할 질문 |
|---|---|
| `upstream/main...HEAD` 전체 | 판정 대상 요청은 grade `/report` 이관 1건이 맞는가? 맞다면 웹 화면 · 문서 · Skill · item-bank 베이스라인을 이번 PR 범위에서 뺄 것인가? |
| `db/mssql/init/02-seed.sql`, `build.gradle`, `application.yml` | `CLAUDE.md` 가 요구하는 사전 승인을 받았는가? |
| 문항 은행 레거시(8081) | 전체 `npm test` 를 완료 기준으로 쓸 것인가? 쓴다면 8081 기동이 필요함 |

## 이슈 0건 재점검 메모

치명 이슈는 없다. 이관 로직(`GradeReportService`)은 성적 계산 규칙(BR-01 · 02 · 05 · 06 · 18~22 · 24~28)을 코드 주석의 `REP:줄번호` 와 대응해 옮겼고 레거시·새 API 스냅샷 16건이 모두 일치한다. 컨트롤러는 `*Service` 만 주입받고 `record` DTO 를 반환하며, `try/catch` 는 리포지토리에서 로그 후 다른 예외로 감싸 던지고(`GradeReportRepository.java:77-84`) HTTP 변환은 `GlobalExceptionHandler.java:47-51` 한 곳에 있다. `@Test` 15건 모두 한국어 `@DisplayName` 이 있고, `@Disabled` · `System.out` · 와일드카드 import · `@Autowired` 는 새 코드에 없다. 웹은 `any`/`ts-ignore`/`console.log`/스냅샷/`getByTestId` 없음, 로딩 · 오류 · 빈 결과 문구가 구분됨.
