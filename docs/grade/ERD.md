# 성적 집계(grade) 데이터 흐름 · ERD

> 분석 대상: `legacy/grade-mssql` · 선행 문서: `docs/grade/ARCHITECTURE.md` · 근거 경로는 저장소 맨 위 기준
>
> 스키마는 모듈 밖 `db/mssql/init/01-schema.sql` 에 있다(컨테이너 첫 기동 때 실행, `db/mssql/init/01-schema.sql:4`). 모듈 안 `legacy/grade-mssql/` 에는 스키마 정의 파일이 없다.

## 테이블 목록

| 테이블 | 주요 컬럼 | 키 · 제약 | 근거 |
|---|---|---|---|
| `dbo.class` | `id` VARCHAR(10), `name` NVARCHAR(50) | PK `id` | `db/mssql/init/01-schema.sql:22-25` |
| `dbo.student` | `id` VARCHAR(20), `name`, `class_id` | PK `id`, FK `class_id → class.id` | `db/mssql/init/01-schema.sql:32-37` |
| `dbo.unit` | `code` VARCHAR(16), `name`, `weight` DECIMAL(3,2) 기본 0.00 | PK `code` | `db/mssql/init/01-schema.sql:44-48` |
| `dbo.assignment` | `id` VARCHAR(10), `unit_code`, `title`, `due_at` DATETIME2(0) NOT NULL | PK `id`, FK `unit_code → unit.code` | `db/mssql/init/01-schema.sql:55-61` |
| `dbo.submission` | `id` INT, `student_id`, `unit_code`, `assignment_id`, `score` DECIMAL(5,1) NULL, `submitted_at`, `status` CHAR(1) 기본 'S', `aggregated_at`, `reported_at` | PK `id`, FK 3개(아래), 인덱스 `(student_id, assignment_id, submitted_at)` | `db/mssql/init/01-schema.sql:69-87` |
| `dbo.grade_summary` | `class_id`, `student_id`, `unit_code`('TOTAL' 포함), `raw_score` NULL, `score` DECIMAL(5,1), `grade` CHAR(1), `is_late`, `is_missing`, `aggregated_at`, `last_report_at` | PK `(student_id, unit_code)`, 인덱스 `class_id`, **FK 없음** | `db/mssql/init/01-schema.sql:94-110` |

- 프로시저 안의 임시 테이블(`#stu` · `#unit` · `#valid` · `#calc` · `#eff` · `#rep`)은 실행 중에만 있으므로 위 목록에서 뺐다 (`legacy/grade-mssql/sql/usp_aggregate_grades.sql:57-91`, `legacy/grade-mssql/sql/usp_class_report.sql:56-110`).

## 테이블 관계

```mermaid
erDiagram
    class ||--o{ student : "선언 FK_student_class"
    unit ||--o{ assignment : "선언 FK_assignment_unit"
    student ||--o{ submission : "선언 FK_submission_student"
    unit ||--o{ submission : "선언 FK_submission_unit"
    assignment ||--o{ submission : "선언 FK_submission_assignment"
    student ||--o{ grade_summary : "추정 student_id"
    class ||--o{ grade_summary : "추정 class_id"
    unit ||--o{ grade_summary : "추정 unit_code (TOTAL 제외)"

    class { varchar id PK
            nvarchar name }
    student { varchar id PK
              nvarchar name
              varchar class_id FK }
    unit { varchar code PK
           nvarchar name
           decimal weight }
    assignment { varchar id PK
                 varchar unit_code FK
                 nvarchar title
                 datetime2 due_at }
    submission { int id PK
                 varchar student_id FK
                 varchar unit_code FK
                 varchar assignment_id FK
                 decimal score
                 datetime2 submitted_at
                 char status
                 datetime2 aggregated_at
                 datetime2 reported_at }
    grade_summary { varchar class_id
                    varchar student_id PK
                    varchar unit_code PK
                    decimal raw_score
                    decimal score
                    char grade
                    bit is_late
                    bit is_missing
                    datetime2 aggregated_at
                    datetime2 last_report_at }
```

| 관계 | 구분 | 근거 |
|---|---|---|
| `student.class_id → class.id` | 선언 | `db/mssql/init/01-schema.sql:35-36` |
| `assignment.unit_code → unit.code` | 선언 | `db/mssql/init/01-schema.sql:57-58` |
| `submission.student_id → student.id` | 선언 | `db/mssql/init/01-schema.sql:71-72` |
| `submission.unit_code → unit.code` | 선언 | `db/mssql/init/01-schema.sql:73-74` |
| `submission.assignment_id → assignment.id` | 선언 | `db/mssql/init/01-schema.sql:75-76` |
| `grade_summary.student_id → student.id` | 추정 | `#stu`(student 에서 적재)의 학생으로 `#calc` 를 만들어 저장 — `legacy/grade-mssql/sql/usp_aggregate_grades.sql:96-100`, `:347-349` |
| `grade_summary.class_id → class.id` | 추정 | 입력 `@class_id` 를 그대로 저장 — `legacy/grade-mssql/sql/usp_aggregate_grades.sql:334`, `:349` |
| `grade_summary.unit_code → unit.code` | 추정 | 단원 행은 `#unit`(unit 에서 적재)의 코드. 단 `'TOTAL'` 행은 unit 에 없는 값 — `legacy/grade-mssql/sql/usp_aggregate_grades.sql:104-105`, `:306` |

- `submission` 은 `unit_code` 와 `assignment_id` 를 둘 다 가진다. 과제도 `unit_code` 를 가지므로 단원 정보가 두 곳에 있다. 두 값이 서로 맞는지 강제하는 제약은 스키마에 없다 (`db/mssql/init/01-schema.sql:57-58`, `:73-76`).
- "과제는 단원당 1개"는 주석에만 있고 제약(UNIQUE)은 없다 (`db/mssql/init/01-schema.sql:52`, `:55-61`).

## 데이터 흐름

```
[GET /aggregate] → usp_aggregate_grades
   class(존재 확인) → student(#stu) → unit ⟕ assignment(#unit) → submission(#valid: 유효 제출)
   → 커서 계산(#calc) → grade_summary UPSERT · DELETE → submission.aggregated_at → 결과 SELECT

[GET /report] → usp_class_report
   student(재적 수) → submission ⋈ student ⋈ assignment(#eff) → unit ⟕ #eff ⟕ submission[X](#rep)
   → submission.reported_at → grade_summary.last_report_at → 결과 SELECT
```

- 근거: `legacy/grade-mssql/sql/usp_aggregate_grades.sql:39-132`, `:333-430` / `legacy/grade-mssql/sql/usp_class_report.sql:33-35`, `:65-155`, `:164-193`
- 두 프로시저는 같은 원천(submission)에서 **따로** 계산한다. 보고 프로시저는 grade_summary 의 점수를 읽지 않고 보고 시각만 쓴다 (`legacy/grade-mssql/sql/usp_class_report.sql:13`, `:172-175`).

## 읽기 · 쓰기 위치 표

Java 쪽은 `GradeRepository.aggregateGrades` · `classReport` 가 프로시저를 호출할 뿐 테이블을 직접 읽거나 쓰지 않는다 (`legacy/grade-mssql/src/main/java/com/example/grade/GradeRepository.java:25`, `:30`). 아래 "함수"는 저장 프로시저다.

| 테이블 | 읽기(SELECT) | 쓰기(INSERT · UPDATE · DELETE) |
|---|---|---|
| `class` | `usp_aggregate_grades` — 학급 존재 확인 `legacy/grade-mssql/sql/usp_aggregate_grades.sql:39-41` | 없음 |
| `student` | `usp_aggregate_grades` — 학급 학생 `legacy/grade-mssql/sql/usp_aggregate_grades.sql:96-100` / `usp_class_report` — 재적 수 `legacy/grade-mssql/sql/usp_class_report.sql:33-35`, 조인 `:86-88`, `:149-151`, `:167-169` | 없음 |
| `unit` | `usp_aggregate_grades` — 전 단원 + 가중치 `legacy/grade-mssql/sql/usp_aggregate_grades.sql:104-109` / `usp_class_report` — 전 단원 `legacy/grade-mssql/sql/usp_class_report.sql:113-115`, `:134` | 없음 |
| `assignment` | `usp_aggregate_grades` — 마감 시각 `legacy/grade-mssql/sql/usp_aggregate_grades.sql:105-108` / `usp_class_report` — 마감 시각 `legacy/grade-mssql/sql/usp_class_report.sql:91-92` | 없음 |
| `submission` | `usp_aggregate_grades` — 유효 제출 `legacy/grade-mssql/sql/usp_aggregate_grades.sql:115-132` / `usp_class_report` — 유효 제출 `legacy/grade-mssql/sql/usp_class_report.sql:75-93`, 무효 건수 `:146-155` | UPDATE `aggregated_at` — `usp_aggregate_grades` `legacy/grade-mssql/sql/usp_aggregate_grades.sql:409-413` / UPDATE `reported_at` — `usp_class_report` `legacy/grade-mssql/sql/usp_class_report.sql:164-169` |
| `grade_summary` | `usp_aggregate_grades` — 저장 검증 `legacy/grade-mssql/sql/usp_aggregate_grades.sql:385-388`, UPSERT 존재 확인 `:351-356` | UPDATE `legacy/grade-mssql/sql/usp_aggregate_grades.sql:333-344`, INSERT `:347-356`, DELETE `:359-367` — `usp_aggregate_grades` / UPDATE `last_report_at` — `usp_class_report` `legacy/grade-mssql/sql/usp_class_report.sql:172-175` |

## 미확인

- `grade_summary` 의 `class_id` · `student_id` · `unit_code` 를 다른 프로그램(배치 · 다른 앱)이 읽거나 쓰는지: 이 저장소 밖은 확인하지 않았다.
- `submission.status` 에 `S` · `X` 말고 다른 값이 들어오는지: 스키마 주석에는 두 값만 있고(`db/mssql/init/01-schema.sql:65`) CHECK 제약이 없다. 운영 데이터는 보지 않았다.
- `submission.unit_code` 와 `assignment.unit_code` 가 어긋난 행이 있는지: 시드와 운영 데이터를 조회하지 않았다.
- 실행 중인 DB 의 스키마가 `01-schema.sql` 과 같은지: DB 에 접속해 조회하지 않았다.
