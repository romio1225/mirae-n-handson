# 변경 영향도 — `dbo.usp_aggregate_grades`

> `/impact usp_aggregate_grades` 결과 · 정적 분석(코드 수정 · 기동 · DB 접속 없음) · 근거 경로는 저장소 맨 위 기준
>
> 약칭: `AGG` = `legacy/grade-mssql/sql/usp_aggregate_grades.sql`, `REP` = `legacy/grade-mssql/sql/usp_class_report.sql`, `CTL` = `legacy/grade-mssql/src/main/java/com/example/grade/GradeController.java`, `REPO` = `legacy/grade-mssql/src/main/java/com/example/grade/GradeRepository.java`
>
> 검색 범위: 저장소 전체(`node_modules` · `build` · `.gradle` · `dist` · `.git` · `gradle` 제외). 검색어: `usp_aggregate_grades`, `/aggregate`, `aggregateGrades`, `grade_summary`, `sp_executesql`, `EXEC *(`

## 1. 대상

| 정의 | 위치 | 비고 |
|---|---|---|
| 분석용 사본 | `AGG:23` | 모듈 안 `sql/` |
| **실제로 실행되는 쪽** | `db/mssql/init/04-procs.sql:28` | 컨테이너 DB 첫 기동 때 만든다. 사본과 본문이 같다고 적혀 있다(`db/mssql/init/04-procs.sql:3`, `AGG:10-11`) |

- 한 줄 요약: 학급 하나의 학생 × 전 단원 점수를 계산해 `grade_summary` 에 UPSERT · DELETE 하고, 그 학급 학생의 제출 행에 `aggregated_at` 을 찍은 뒤 결과 행(`student_id, name, unit, score, grade`)을 돌려준다.
- **고칠 때 두 파일을 같이 바꿔야 한다.** `AGG` 만 고치면 실행되는 프로시저는 바뀌지 않는다.

## 2. 영향받는 화면 · 진입점

| URL · 화면 | 진입 메서드 | 영향 | 근거 |
|---|---|---|---|
| `GET /aggregate?class_id=` — 성적 집계 표 `<table id="grades">` | `GradeController.aggregate` | **직접** — 결과 행을 그대로 표로 그린다 | `CTL:46-60` |
| `GET /report?class_id=` — 학급 단원별 현황 | `GradeController.report` | **간접** — 같은 테이블(`grade_summary` · `submission`)을 반대 순서로 잠그고 쓴다 | `CTL:62-79`, `REP:158-175` |
| `GET /` — 안내 화면 | `GradeController.index` | 링크만 있음(호출 아님) | `CTL:38` |
| 저장소 밖 `export_grades` 배치 | 없음(로그만) | **간접** — `grade_summary` 를 읽어 내보낸다 | `pipeline-samples/batch-logs/2026-09-08.log:11` |
| DB 계정 `readonly` 로 직접 `EXEC` | 없음 | 직접 — 실행 권한이 있다 | `db/mssql/init/04-procs.sql:648` |

## 3. 호출 경로

```
브라우저 GET /aggregate
  → GradeController.aggregate                       CTL:46  (@GetMapping "/aggregate")
  → GradeRepository.aggregateGrades(cid)            CTL:50
  → JdbcTemplate.query("EXEC dbo.usp_aggregate_grades @class_id = ?")   REPO:25
  → dbo.usp_aggregate_grades                        db/mssql/init/04-procs.sql:28 (사본 AGG:23)
```

- `class_id` 는 `normalize` 를 거친다. 비었으면 `C1` 이다(`CTL:48`, `:83-88`).
- 저장소 안의 호출은 이 경로 하나뿐이다. `modern/` 에서 `grade_summary` · `aggregate` · `mssql` 을 검색한 결과는 0건이다.

## 4. 영향받는 테이블

| 테이블 | 대상의 읽기 / 쓰기 | 같은 테이블을 쓰는 다른 코드 |
|---|---|---|
| `class` | 읽기 `AGG:39-41` | 없음(저장소 안) |
| `student` | 읽기 `AGG:96-100` | `REP:33-35`, `:167-169` (읽기) |
| `unit` · `assignment` | 읽기 `AGG:104-109` | `REP:91-92`, `:113-115` (읽기) |
| `submission` | 읽기 `AGG:127` / **쓰기** `aggregated_at` `AGG:409-413` | `REP` 가 읽고(`:75-93`) **쓴다** `reported_at` (`REP:164-169`) |
| `grade_summary` | **쓰기** UPDATE `AGG:333-344`, INSERT `:347-356`, DELETE `:359-367` / 읽기(검증) `:385-388` | `REP` 가 **쓴다** `last_report_at` (`REP:172-175`). 저장소 밖 `export_grades` 배치가 읽는다(`pipeline-samples/batch-logs/2026-09-08.log:11`) |

- **잠금 순서가 반대다.** 집계는 `grade_summary` → `submission`(`AGG:327-328`, 트랜잭션 `:330`~`:415`), 보고는 `submission` → `grade_summary`(`REP:158-159`, 트랜잭션 `:161`~`:177`).
- 실제 교착이 기록돼 있다. `lock_wait ... proc=grades.dbo.usp_class_report line=172 blocker_proc=grades.dbo.usp_aggregate_grades`(`incident-logs/d-mssql-deadlock/mssql-wait.log:19`), `/report` 가 deadlock 으로 502(`incident-logs/d-mssql-deadlock/app-timeout.log:121`). 저장 구간(`:327-415`)을 고치면 이 교착에 직접 영향이 있다.
- DELETE 는 `class_id = @class_id` 조건이 있다(`AGG:361`). 다른 학급의 행은 지우지 않는다. 반면 집계 결과가 줄면 그 학급의 옛 행은 지운다.
- 보고의 `last_report_at` 은 집계가 만든 `grade_summary` 행에만 찍힌다(`REP:172-175`). 집계가 행을 지우거나 키를 바꾸면 보고 시각 기록도 달라진다.

## 5. 관련 규칙 ID (`docs/grade/BUSINESS-RULES.md`)

줄번호 대조: BR-15 `AGG:333-367`, BR-17 `AGG:409-413`, BR-13 `AGG:428-430` 을 다시 열어 현재 코드와 맞는지 확인했다.

| 구역 | 규칙 ID | 겹치는 줄 |
|---|---|---|
| 대상 선별 | BR-01 제외 `X` · BR-02 최신 1건 · BR-03 전 단원 · BR-04 과제 없는 단원 | `AGG:104-132` |
| 점수 | BR-05 NULL→0 · BR-06 지연 감점 · BR-07 미제출 0점 · BR-08 보너스 미제출 행 없음 · BR-27 자릿수 | `AGG:85-86`, `:177-214` |
| 등급 · 종합 | BR-09 등급 구간 · BR-10 하드코딩 가중치 · BR-11 종합 등급 · BR-12 종합 플래그 | `AGG:236-317` |
| 저장 · 부수효과 | BR-14 없는 학급 · BR-15 UPSERT·DELETE · BR-16 저장 검증 · BR-17 `aggregated_at` 전 행 | `AGG:43-52`, `:333-413` |
| 결과 | BR-13 정렬 | `AGG:428-430` |
| 호출부(Java) | BR-24 기본 학급 C1 · BR-25 값 서식 · BR-26 DB 오류 → 502 | `CTL:23`, `:57-58`, `REPO:45-53` |
| 같은 규칙의 다른 구현 | BR-01 · 02 · 05 · 06 은 `REP` 에도 따로 있다(`REP:13` 주석 "같아야 하지만 별도로 구현") | 한쪽만 고치면 두 화면 숫자가 달라진다 |

## 6. 수정 전에 확보할 테스트 케이스

Day 1-3 동작 보존 테스트의 입력 후보다. 현재 `characterization/tests/` 에는 `grade` 용 테스트가 없다(`example-units.test.js`, `normalize.unit.test.js` 뿐). 기준 URL 은 준비돼 있다(`characterization/lib/target.mjs:11`, `characterization/scripts/baseline.mjs:11`).

| # | 입력 조건 | 기대 결과(현재 동작) | 근거 |
|---|---|---|---|
| 1 | `/aggregate?class_id=C1` 정상 | 열 `student_id, name, unit, score, grade`, 학번 순 → 단원 → TOTAL 마지막 | `CTL:54`, BR-13 `AGG:428-430` |
| 2 | `class_id` 없음 · 공백 | `C1` 로 집계 | BR-24 `CTL:83-88` |
| 3 | 없는 학급(예: `C9`) | 빈 결과. `grade_summary` · `submission` 변화 없음 | BR-14 `AGG:43-52` |
| 4 | 같은 과제 재제출 2건, 시각 다름 / 시각 같음 | 늦은 것 / id 큰 것 인정 | BR-02 `AGG:123-126` |
| 5 | 상태 `X` 제출만 있는 학생 | 그 단원 미제출 처리 | BR-01 `AGG:130`, BR-07 |
| 6 | 점수 NULL 제출 | 0점 | BR-05 `AGG:202-203` |
| 7 | 제출 시각이 마감 + 2일 경계 바로 앞 · 정확히 · 바로 뒤 | 정확히 마감+2일은 지연 아님(`>` 비교), 그보다 늦으면 지연 · `ROUND(점수×0.9, 1)` | BR-06 `AGG:209`, `:213` |
| 8 | 단원 점수 59.9 / 60.0 / 89.9 / 90.0 | D·F 경계, A·B 경계 | BR-09 `AGG:236-243` |
| 9 | 보너스 단원(가중치 0.00) 미제출 | 그 단원 행 없음 | BR-08 `AGG:189-194` |
| 10 | 과제가 없는 단원 | 모든 학생 미제출 | BR-04 `AGG:107-108` |
| 11 | `unit.weight` 를 바꾼 뒤 집계 (조건 없음) | TOTAL 은 바뀌지 않음(하드코딩 가중치) | BR-10 `AGG:281-290` |
| 12 | 같은 학급 두 번 연속 집계 | 두 번째 결과 · `grade_summary` 가 첫 번째와 같음(UPSERT) | BR-15 `AGG:333-356` |
| 13 | 집계 결과에서 빠진 (학생, 단원) 행이 `grade_summary` 에 있을 때 | 그 학급 행만 삭제, 다른 학급 행은 유지 | BR-15 `AGG:359-367` |
| 14 | 집계 뒤 `submission.aggregated_at` (부수효과) | `X` 포함 그 학급 학생 모든 제출 행 갱신 | BR-17 `AGG:409-413` |
| 15 | `/aggregate` 와 `/report` 같은 학급 동시 호출 | 현재는 교착 가능 → 한쪽 502 | `AGG:327-328`, `REP:158-159`, BR-26 |
| 16 | DB 중단 상태에서 `/aggregate` | HTTP 502 + 원인 메시지 | BR-26 `CTL:57-58` |

- 11 · 14 는 "조건이 없는" 동작이고, 12 · 13 · 14 는 쓰기 부수효과다. 이관 뒤에도 유지할지는 사람이 정한다.

## 7. 추적 한계

- **저장소 밖 호출자가 있다.** `grade-client` 가 `http://10.20.4.15:8083/aggregate` 를 부른 로그가 있다(`incident-logs/d-mssql-deadlock/app-timeout.log:1`). 이 클라이언트의 코드는 저장소에 없어서 호출 조건 · 빈도는 미확인이다.
- **`export_grades` 배치**가 `grade_summary` 를 읽는다(`pipeline-samples/batch-logs/2026-09-08.log:11`). 배치 코드는 저장소에 없다. 컬럼 의미나 행 구성을 바꾸면 내보내기 결과가 달라질 수 있지만 어느 컬럼을 쓰는지는 미확인이다.
- **`readonly` 계정이 이 프로시저를 실행할 수 있다**(`db/mssql/init/04-procs.sql:648`, `db/mssql/init/03-users.sql:4-5`). 이름은 읽기 전용이지만 이 프로시저를 거치면 `grade_summary` · `submission` 에 쓴다. 누가 실제로 이 계정으로 부르는지는 알 수 없다.
- SQL Agent 잡 · 트리거 등 DB 안의 다른 호출자: 저장소에 정의가 없고 DB 를 조회하지 않았다. 저장소 밖 미확인.
- 동적 호출: `db/` · `legacy/grade-mssql` 에서 `sp_executesql` · `EXEC(` 검색 결과는 0건이다. Java 쪽 호출은 SQL 문자열 속 이름이라 IDE 의 "호출 찾기"로는 안 잡히고 문자열 검색으로만 찾았다(`REPO:25`).
- 실행 중인 DB 의 프로시저가 `04-procs.sql` 과 같은지: DB 를 조회하지 않았다.
