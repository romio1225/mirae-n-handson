# 마이그레이션 검증 리포트 2

- 검증 대상 범위: `upstream/main...HEAD` (7개 커밋, 59개 파일, +7777 / -5)
- 요청 문장: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.
- 리뷰 모델: Sonnet (리뷰 세션, 코드 수정 없음)
- 판정 대상으로 본 이관: grade `GET /report?class_id=` → `GET /api/grades/report?class_id=` (`usp_class_report`)

## 실행한 테스트

| 명령 | 결과 |
|---|---|
| `cd modern/api && ./gradlew cleanTest test` | 통과 39 / 실패 0 / 오류 0 / 건너뜀 0 |
| `cd modern/web && npm run lint && npm run typecheck && npm test` | lint 통과 · typecheck 통과 · 테스트 19 통과 / 0 실패 (8 파일) |
| `cd characterization && npm test` (레거시 기본 주소, 전체) | 58 통과 / 0 실패 (4 파일) |
| `cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test` (새 API, 전체) | 29 통과 / 28 실패 / 1 건너뜀 — 실패는 모두 `item-bank.test.js` 28건(`/api/items/search` 가 레거시 케이스에 400 응답) |
| `cd characterization && TARGET_BASE_URL=http://localhost:8080 npx vitest run tests/grade.test.js` (새 API, grade 만) | 16 통과 / 0 실패 |

grade 이관의 동작 보존은 새 API 기준 16/16 통과했다. 전체 명령의 28건 실패는 item-bank 베이스라인이 새 API 에 대응 이관 없이 이 범위에 들어와 있기 때문이다(이슈 3).

## 판정: 반려

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 충족 (grade 한정) | api 39/0, web lint · typecheck · test 19/0, 레거시 characterization 58/0, 새 API grade 16/0. 단 새 API 전체 `npm test` 는 28 실패(item-bank, 범위 밖 모듈). 이슈 3, 5 참조 |
| 치명 이슈 0건 | 충족 | 치명 없음. 리터럴 비밀번호 없음(`application.yml:34` 환경 변수), SQL 은 파라미터 바인딩(`GradeReportRepository.java:28-40`), 삭제 · 덮어쓰기 코드 없음 |
| 요청 범위 이탈 없음 | 미충족 | 요청은 "엔드포인트 1개 이관"인데 범위에 web 화면, item-bank 베이스라인(스냅샷 2721줄), docs · Skill · CLAUDE.md, DB 시드, 의존성, 접속 설정이 함께 있다. 이슈 2, 3 |
| 컨벤션 준수 | 미충족 | 이슈 1 (`@Transactional(readOnly = true)` 규칙) |

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `modern/api/src/main/java/com/example/grade/GradeReportService.java:29-30` | CLAUDE.md 는 조회 서비스 메서드에 `@Transactional(readOnly = true)` 를 요구하나 의도적으로 생략했다(주석 `:23-25`, MariaDB 풀 연결 점유 회피). 생략의 부작용: `classReport` 가 `countEnrolled`(`:63`)와 `findClassSubmissions`(`:68`)를 별도 조회로 하므로 두 조회 사이 데이터가 바뀌면 재적 수와 제출 집계가 어긋날 수 있다 | 사람이 예외를 승인하고 CLAUDE.md 에 "MS-SQL 전용 서비스는 예외"를 명시하거나, MS-SQL 전용 트랜잭션 관리자를 두어 규칙을 지킨다 |
| 2 | 경고 | `modern/api/build.gradle:28`, `modern/api/src/main/resources/application.yml:30-37`, `db/mssql/init/02-seed.sql:223-258` | CLAUDE.md 가 "바꾸기 전에 사람에게 묻는다"고 한 파일(의존성 · 접속 설정 · `db/` 시드)이 요청 문장에 없는 채 바뀌었다. 시드 C4 추가(학급 1 · 학생 5 · 제출 10)는 레거시 DB 상태를 바꾸므로 이관 대상 레거시의 응답 근거가 달라질 수 있다 | 변경 승인 기록을 남기거나(요청 · 사람 승인 문장), 시드 변경을 별도 요청 · 커밋으로 분리한다 |
| 3 | 경고 | `characterization/tests/item-bank.test.js:1`, `characterization/__snapshots__/item-bank.test.js.snap:1`, `modern/web/src/App.tsx:1-27`, `modern/web/src/components/GradeReportView.tsx:141`, `modern/web/src/styles.css:1` | "엔드포인트 1개 이관" 요청으로 설명되지 않는 변경이 범위에 섞여 있다: item-bank 베이스라인(새 API 대상 실행 시 28건 실패), 성적 현황 화면 · 탭 · CSS 297줄, docs · Skill · CLAUDE.md. 커밋 범위가 7건이라 어느 요청을 판정하는지 모호하다 | grade `/report` 이관 커밋(`f0c6e7c`, `265a253`, `e002d4f`)만 좁혀 재검증하거나, web · item-bank 를 별도 요청으로 분리해 각각 판정한다 |
| 4 | 경고 | `modern/api/src/main/java/com/example/grade/GradeReportService.java:26-27` | "동작을 바꾸지 않고" 와 불일치: 레거시가 조회마다 하는 `reported_at` · `last_report_at` 기록(BR-23, `docs/grade/BUSINESS-RULES.md:276-281`)을 이관하지 않았다. 응답에 안 나와 테스트가 못 잡는다(문서 `:419`, `:431`에 의도적 누락으로 기록). 502 본문 문구도 DB 원문 대신 일반 문구로 달라졌다(`DatabaseUnavailableException.java:5-8`) | 사람이 "쓰기 부수효과 미이관"을 명시 승인한다. 승인 없이는 이관 완료로 보지 않는다 |
| 5 | 경고 | `modern/api/src/main/java/com/example/grade/GradeReportRepository.java:56,71` | `findClassSubmissions` · `findUnits` 의 SQL(JOIN · ORDER BY)을 검증하는 단위 · 슬라이스 테스트가 없다(`GradeReportRepositoryTest.java` 는 `countEnrolled` 오류 변환 1건만). 이 SQL 은 라이브 MS-SQL 대상 characterization 으로만 확인된다 | Repository 의 SQL 문자열 · 파라미터를 검증하는 Mockito 테스트를 추가하거나, characterization 이 SQL 을 보장한다는 점을 문서로 남긴다 |
| 6 | 제안 | `modern/web/src/components/ClassPicker.tsx:25` | CLAUDE.md "컴포넌트 내부 핸들러 함수 이름은 `handle<동작>`" — 인라인 화살표 `() => onSelect(option.id)` 사용(같은 PR 의 `ModuleTabs.tsx:21` 은 `handleSelect` 를 씀) | `handleSelect` 로 이름 붙인 함수로 뽑는다 |
| 7 | 제안 | `modern/web/src/components/GradeReportView.tsx:10-14` | 학급 목록을 코드에 고정(C1~C3). 시드의 C4 는 화면에서 선택할 수 없고, 서버의 학급 목록 API 가 없다 | 학급 목록 출처(API 또는 상수 위치)를 확정한다 |

## 확인 필요

| 파일 또는 범위 | 확인할 질문 |
|---|---|
| `GradeReportService.java:103` | `submittedAt` · `dueAt` 가 DB 에서 NOT NULL 인지(NULL 이면 NPE → 500). 스키마 DDL 로 확인 필요(근거 줄번호 없어 이슈로 올리지 않음) |
| 범위 전체 | 이번 판정을 grade `/report` 이관 단일 요청으로 좁혀도 되는지, web · item-bank · 문서를 별도 요청으로 볼지 |

## 점검한 것(문제 없음)

- 컨트롤러는 `*Service` 만 주입, 엔티티 반환 없음, 경로 `/api/grades/...` (`GradeReportController.java:13-26`).
- `src/main` 의 `@Autowired` · `System.out` · `printStackTrace` · 인자 없는 `now()` · 와일드카드 import 없음. `catch` 는 로그 후 래핑 예외 던짐(`GradeReportRepository.java:80-83`). 로그에 학생 식별자 · 이메일 · 토큰 없음.
- 매직 넘버는 이름 있는 상수(`GradeReportService.java:34-51`). T-SQL 나눗셈 자릿수 6 버림은 상수와 주석으로 반영(`:49`, `:172`).
- 키 비교의 대소문자 · 뒤 공백 무시는 `sqlKey`(`:180-186`)로 반영, C4 경계 케이스로 characterization 통과.
- web: `any` · `as unknown as` · `console.log` · `export default` · `getByTestId` 없음. 로딩 · 오류 · 빈 결과 문구가 각각 다르다(`AsyncSection.tsx`, `GradeReportView.tsx:91`). 응답 타입 필드명은 `UnitReportResponse` 의 `@JsonProperty` 와 같다. 새 컴포넌트마다 `.test.tsx` 있고 렌더 + 상호작용 검증이 있다.
- `characterization/` 의 테스트 · 스냅샷은 이 범위에서 신규 추가이며 기존 파일 수정이 아니다. 새 API 대상 grade 16/16 통과.
