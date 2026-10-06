# 이관 결과 재검증 3회차 — `/verify` (reviewer · tester Sub-agent)

- 검증 대상: `upstream/main...HEAD -- modern characterization` + 커밋 전 수정분(`git diff 1bb4f2f -- modern characterization`, 31개 파일)
- 요청 범위: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관 — grade `GET /report?class_id=` → `GET /api/grades/report?class_id=` (`usp_class_report`)
- 절차: `.claude/skills/verify/SKILL.md` — 3단계 체크리스트 대조는 `reviewer`, 4단계 테스트 실행은 `tester` Sub-agent 에 위임(동시 실행), 5단계 최종 판정은 메인 세션
- 이번 회차 직전 수정(리뷰어 단독 호출 지적 반영): `GradeReportService.java` 상태 `'X'` 비교를 DB 정렬 규칙대로(`isExcluded`, W3), 단원 code · name trim(W4)

## 판정: 반려

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 충족 (grade 한정) | tester 실행: api `./gradlew test` 41/0(추가 2건 포함), web `npm test` 19/0, characterization 레거시 대상 58/0, 새 API 대상 grade 16/16. 새 API 전체는 29 통과 / 28 실패 / 1 건너뜀 — 실패 28건은 모두 `item-bank.test.js`(`/api/items/search` 미이관, 400 응답)로 이번 요청 범위 밖이며 `migration-review-2.md:15` 와 같은 수치(회귀 아님) |
| 치명 이슈 0건 | 충족 | 비밀번호 리터럴 없음(`application.yml` 의 `${GRADES_DB_PASSWORD:}`), SQL 값은 `:classId` 바인딩(`GradeReportRepository.java:31-36`), `legacy/` 변경 0줄, characterization 기존 테스트 · 스냅샷 수정 없음 |
| 요청 범위 이탈 없음 | 미충족 | 이슈 1, 2 |
| 컨벤션 준수 | 충족 | reviewer 대조 결과 위반 없음(readOnly 생략 사유는 클래스 주석 `GradeReportService.java:23-24` 에 있고 CLAUDE.md 예외 조건에 맞음) |

남은 미충족 사유(한 줄): 코드 결함은 없고, 비교 범위에 이관 외 커밋(item-bank 베이스라인)이 섞여 있으며 의존성 · 접속 설정 변경의 사람 승인 기록이 diff 에 없다.

reviewer 와 tester 보고는 엇갈리지 않았다(reviewer: 새 로직마다 테스트 존재, tester: 미커밋 수정분 2건에 테스트가 없어 추가 후 통과).

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `characterization/tests/item-bank.test.js:1-134`, `characterization/__snapshots__/item-bank.test.js.snap` | 문항 은행 search.php 베이스라인(커밋 5cb2c6a). "grade 엔드포인트 1개 이관"으로 설명되지 않는 다른 모듈 파일이고, 새 API 대상 28건 실패의 출처다 | 검증 범위를 grade 이관 커밋으로 좁히거나 item-bank 베이스라인을 별도 요청 · PR 로 분리 |
| 2 | 경고 | `modern/api/build.gradle:27-28`, `modern/api/src/main/resources/application.yml:28-36` | `mssql-jdbc` 의존성과 `grades.datasource` 접속 · 풀 설정(최대 2 · 대기 3000ms) 추가. CLAUDE.md 금지 사항상 사전 승인 대상인데 승인 기록이 diff · 커밋 메시지에 없다 | 사람 승인 기록을 PR 설명에 남긴 뒤 재판정 |

## 확인 필요

| 파일 또는 범위 | 확인할 질문 |
|---|---|
| `build.gradle:27-28`, `application.yml:28-36` | 의존성 · 접속 설정 변경을 누가 언제 승인했는가(이슈 2) |
| `GradeReportRepository.java:26`, `GlobalExceptionHandler.java:47-51` | 502 본문을 DB 원문 대신 고정 문구로 바꾼 것(BR-26)을 "동작을 바꾸지 않고"의 예외로 사람이 승인했는가 |
| `legacy/grade-mssql/sql/usp_class_report.sql:161-177` | 보고 시각 UPDATE(BR-23) 미이관을 사람이 승인했는가(readonly 계정이라 쓰지 않음) |
| 이슈 표 전 항목 | 체크리스트 0-3 에 따라 줄번호를 사람이 원본과 대조 |

## reviewer 확인 요청 중 메인 세션이 DB `SELECT` 로 해소한 것

로컬 compose MS-SQL(`grades`, readonly 계정)에서 확인했다.

| 질문 | SELECT 결과 | 결론 |
|---|---|---|
| 평균 나눗셈 자릿수(`GradeReportService.java:48`) | `CAST(2 AS DECIMAL(38,1))/3` = 0.666666, scale 6 · 리터럴 `2.0/3` 도 scale 6 | 상수 `DIVISION_SCALE = 6` · 버림 처리와 주석 모두 맞음 |
| 단원 정렬 collation(레거시 `#rep` vs 현행 `dbo.unit.code`) | tempdb · `dbo.unit.code` 모두 `SQL_Latin1_General_CP1_CI_AS` | 정렬 순서 차이 없음 |
| 상태 비교 collation(W3 근거) | `dbo.submission.status` = `SQL_Latin1_General_CP1_CI_AS`, `'x' = 'X'` 참, 뒤 공백 무시 | `isExcluded` 수정이 레거시와 일치 |

## tester 가 추가한 테스트

`modern/api/src/test/java/com/example/grade/GradeReportServiceTest.java` (테스트 폴더 안, 소스 수정 없음)

- `excludedStatusFollowsCaseInsensitiveCollation` — 소문자 `x` 와 `'X '` 도 집계에서 빼고 제외 수에 센다
- `unitCodeAndNameAreTrimmedInResponse` — 단원 코드 · 이름을 trim 해 내보내고 뒤 공백 코드도 같은 단원으로 묶는다

## 1회차(`migration-review-1.md`)와 비교

| 회차 | 판정 | 이슈 | 코드 결함 |
|---|---|---|---|
| 1회차 | 반려 | 5건(경고 4 · 제안 1) | readOnly 규칙 해석 등 |
| 3회차 | 반려 | 2건(경고 2) | 0건 — 남은 2건은 모두 범위 · 승인 기록 문제 |

동작 보존상 잠재 차이(소문자 상태값, 단원 문자열 공백)는 이번 회차 직전에 수정 · 테스트로 고정했다.
