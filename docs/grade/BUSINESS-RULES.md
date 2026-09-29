# 성적 집계(grade) 비즈니스 규칙 후보

> 분석 대상: `legacy/grade-mssql` · 선행 문서: `docs/grade/ARCHITECTURE.md`, `docs/grade/ERD.md` · 근거 경로는 저장소 맨 위 기준
>
> 특히 본 곳: `usp_aggregate_grades` 와 `usp_class_report` 안의 집계 대상 제외 조건, 반올림과 자릿수, 미제출 처리, 단원별 가중치로 보이는 숫자
>
> 약칭: `AGG` = `legacy/grade-mssql/sql/usp_aggregate_grades.sql`, `REP` = `legacy/grade-mssql/sql/usp_class_report.sql`. 근거 칸에는 전체 경로를 적는다.

## 1. 집계 대상 선별

### BR-01
- 규칙: 제출 상태가 `'X'` 이면 집계 · 보고 대상에서 제외한다.
- 근거: `legacy/grade-mssql/sql/usp_aggregate_grades.sql:130`, `legacy/grade-mssql/sql/usp_class_report.sql:89`
- 근거 코드 (`AGG:130`. `REP:89` 는 같은 조건을 `AND sub.status <> 'X'` 로 쓴다):
    ```sql
                 WHERE sub.status <> 'X'
    ```
- 확신도: 확실
- 비고: 매직 문자 `'X'`. 조건이 "X 가 아니면 포함"이라서 `S` 외의 다른 값이 들어와도 포함된다. 스키마에 CHECK 제약이 없다(`db/mssql/init/01-schema.sql:79`). `S=제출, X=무효` 라는 뜻은 주석에만 있다(`db/mssql/init/01-schema.sql:65`).

### BR-02
- 규칙: 같은 학생 · 같은 과제에 제출이 여러 건이면 `submitted_at` 이 가장 늦은 1건만 인정하고, 시각까지 같으면 `id` 가 큰 것을 인정한다.
- 근거: `legacy/grade-mssql/sql/usp_aggregate_grades.sql:123-126`, `:132` / `legacy/grade-mssql/sql/usp_class_report.sql:81-84`, `:93`
- 근거 코드:
    ```sql
                   ROW_NUMBER() OVER (
                       PARTITION BY sub.student_id, sub.assignment_id
                       ORDER BY sub.submitted_at DESC, sub.id DESC
    ```
- 확신도: 확실
- 비고: 두 프로시저에 같은 코드가 따로 있다. BR-01 의 제외가 먼저 적용되므로, 가장 늦은 제출이 `X` 이면 그 앞의 유효 제출이 인정된다.

### BR-03
- 규칙: 집계는 학급과 상관없이 `unit` 테이블의 **모든 단원**을 모든 학생에게 적용한다.
- 근거: `legacy/grade-mssql/sql/usp_aggregate_grades.sql:104-109`, `legacy/grade-mssql/sql/usp_class_report.sql:134`
- 근거 코드:
    ```sql
    SELECT u.code, u.name, u.weight, a.id, a.due_at
      FROM dbo.unit AS u
      LEFT JOIN dbo.assignment AS a
    ```
- 확신도: 확실
- 비고: 단원에 학급 · 학년을 거르는 조건이 없다. 시드에서는 5학년 학급(C1 · C2)도 `M6-*` 단원을, 6학년 학급(C3)도 `M5-*` 단원을 계산한다(`db/mssql/init/02-seed.sql:18-20`, `:24-28`). 의도인지는 미확인.

### BR-04
- 규칙: 과제가 없는 단원이면 모든 학생을 그 단원 미제출로 처리한다.
- 근거: `legacy/grade-mssql/sql/usp_aggregate_grades.sql:107-108`, `:177-184`
- 근거 코드:
    ```sql
             WHERE v.student_id    = @student_id
               AND v.assignment_id = @assignment_id;
    ```
- 확신도: 확실
- 비고: `LEFT JOIN` 으로 들어온 `assignment_id` 가 NULL 이면 `= NULL` 비교가 참이 되지 않아 `@found = 0` 이 된다. 반대로 한 단원에 과제가 2개 이상이면 `#calc` 의 PK `(student_id, unit_code)` 가 중복돼 오류가 날 것으로 보인다(`:90`). 실행해 보지는 않았다. "과제는 단원당 1개"는 주석만 있음(`db/mssql/init/01-schema.sql:52`).

## 2. 점수 계산

### BR-05
- 규칙: 유효 제출의 점수가 NULL 이면 0점으로 본다.
- 근거: `legacy/grade-mssql/sql/usp_aggregate_grades.sql:202-203`, `legacy/grade-mssql/sql/usp_class_report.sql:71-72`
- 근거 코드:
    ```sql
                IF @raw IS NULL
                    SET @raw = 0.0;
    ```
- 확신도: 확실
- 비고: `REP` 는 `ISNULL(v.score, 0.0)` 로 같은 규칙을 따로 구현한다. 주석(`:114`)도 코드와 같다.

### BR-06
- 규칙: 제출 시각이 과제 마감 + 2일을 넘으면 지연으로 표시하고, 점수를 `ROUND(점수 × 0.9, 1)` 로 바꾼다.
- 근거: `legacy/grade-mssql/sql/usp_aggregate_grades.sql:208-214`, `legacy/grade-mssql/sql/usp_class_report.sql:69-74`
- 근거 코드:
    ```sql
                IF @due_at IS NOT NULL
                   AND @submitted_at > DATEADD(DAY, 2, @due_at)
                    SET @adj = ROUND(@raw * 0.9, 1);
    ```
- 확신도: 확실
- 비고: 매직 넘버 `2`(일) · `0.9`. 비교가 `>` 라서 마감 + 정확히 2일에 낸 것은 지연이 아니다. `AGG` 는 `@due_at IS NOT NULL` 을 확인하지만 `due_at` 은 NOT NULL 컬럼이다(`db/mssql/init/01-schema.sql:60`). `REP` 는 이 확인 없이 `JOIN` 으로 같은 계산을 한다. 반올림은 주석(`:212`)대로 5 올림이다.

### BR-07
- 규칙: 가중치가 0 이 아닌 단원을 내지 않았으면 그 단원 점수를 0점으로 하고 미제출로 표시한다.
- 근거: `legacy/grade-mssql/sql/usp_aggregate_grades.sql:184-197`
- 근거 코드:
    ```sql
                SET @is_missing = 1;
                -- 가중치가 있는 단원은 0점으로 집계
                SET @adj = 0.0;
    ```
- 확신도: 확실
- 비고: 이때 가중치는 `unit.weight` 테이블 값을 쓴다(`:105`, `:161`). BR-10 의 종합 계산은 다른 곳의 가중치를 쓴다.

### BR-08
- 규칙: 가중치가 `0.00` 인 단원(보너스)을 내지 않았으면 그 단원 행을 만들지 않는다.
- 근거: `legacy/grade-mssql/sql/usp_aggregate_grades.sql:189-194`
- 근거 코드:
    ```sql
                IF @unit_weight = 0.00
                    FETCH NEXT FROM cur_unit INTO @unit_code, @unit_weight, @assignment_id, @due_at;
                    CONTINUE;
    ```
- 확신도: 확실
- 비고: 매직 넘버 `0.00`. 2023-11 변경 이력(`:17`)과 같다. 행이 없으므로 결과 화면에서 그 학생의 보너스 단원 줄이 빠진다.

## 3. 등급 · 종합

### BR-09
- 규칙: 단원 점수가 90 이상이면 A, 80 이상 B, 70 이상 C, 60 이상 D, 그 밖에는 F 로 한다.
- 근거: `legacy/grade-mssql/sql/usp_aggregate_grades.sql:236-243`
- 근거 코드:
    ```sql
                       WHEN adj_score >= 90.0 THEN 'A'
                       WHEN adj_score >= 80.0 THEN 'B'
                       WHEN adj_score >= 70.0 THEN 'C'
    ```
- 확신도: 확실
- 비고: 매직 넘버 `90.0 / 80.0 / 70.0 / 60.0`. 지연 감점이 적용된 점수(`adj_score`)로 판정한다. 같은 구간이 TOTAL 쪽에 한 번 더 있다(BR-11).

### BR-10
- 규칙: 학생 종합(TOTAL) 점수는 단원 점수 × 단원 가중치를 모두 더한 뒤 소수 첫째 자리로 반올림한다. 가중치는 코드에 적힌 값(`M5-1` 0.30, `M5-2` 0.25, `M5-3` 0.25, `M6-1` 0.20, `M6-2` 0.00, 그 밖에 0.00)을 쓴다.
- 근거: `legacy/grade-mssql/sql/usp_aggregate_grades.sql:281-290`, `:308`
- 근거 코드:
    ```sql
            SET @w = CASE @unit_code
                         WHEN 'M5-1' THEN 0.30
            SET @total = @total + (@unit_score * @w);
    ```
- 확신도: 확실
- 비고: **같은 가중치가 두 곳에 있다.** 종합은 하드코딩한 `CASE` 를, 미제출 판정(BR-07 · BR-08)은 `unit.weight` 를 쓴다. 코드의 TODO 주석(`:280`)과 변경 이력(`:16`)도 이 사실을 적어 두었다. 지금은 시드 값과 같다(`db/mssql/init/02-seed.sql:24-28`). 그러나 `unit.weight` 를 바꾸거나 단원을 추가하면 종합에는 반영되지 않는다. 새 단원은 `ELSE 0.00` 이 되어 종합에서 빠진다. 단원 점수는 이미 소수 첫째 자리 값(`DECIMAL(5,1)`, `:86`, `:250`)을 쓰고, `단원 점수 × 가중치` 곱은 반올림하지 않은 채 `DECIMAL(9,4)` 로 누적한 뒤(`:249`, `:290`) 마지막에 한 번만 반올림한다(`:308`). 가중치 합이 1.00 이라는 것은 주석만 있음(`db/mssql/init/01-schema.sql:41`).

### BR-11
- 규칙: 종합 등급은 반올림한 종합 점수에 BR-09 와 같은 구간(90/80/70/60)을 적용한다.
- 근거: `legacy/grade-mssql/sql/usp_aggregate_grades.sql:311-317`
- 근거 코드:
    ```sql
                WHEN ROUND(@total, 1) >= 90.0 THEN 'A'
                WHEN ROUND(@total, 1) >= 80.0 THEN 'B'
    ```
- 확신도: 확실
- 비고: 등급 구간이 두 곳(`:238-241`, `:312-315`)에 복사돼 있다.

### BR-12
- 규칙: 학생의 단원 중 하나라도 지연 · 미제출이면 종합 행의 `is_late` · `is_missing` 도 1 로 한다.
- 근거: `legacy/grade-mssql/sql/usp_aggregate_grades.sql:292-295`, `:309-310`
- 근거 코드:
    ```sql
            IF @is_late = 1
                SET @has_late = 1;
            IF @is_missing = 1
    ```
- 확신도: 확실
- 비고: 결과 SELECT(`:420-424`)에는 이 두 플래그가 나오지 않는다. `grade_summary` 에만 저장된다.

### BR-13
- 규칙: 집계 결과는 학번 순으로 정렬하고, 한 학생 안에서는 단원 코드 순으로 놓되 TOTAL 행을 맨 뒤에 둔다.
- 근거: `legacy/grade-mssql/sql/usp_aggregate_grades.sql:428-430`
- 근거 코드:
    ```sql
     ORDER BY c.student_id,
              CASE WHEN c.unit_code = 'TOTAL' THEN 1 ELSE 0 END,
              c.unit_code;
    ```
- 확신도: 확실
- 비고: 매직 문자 `'TOTAL'`(`:306`, `:429`). 학번은 VARCHAR 라서 문자열 순으로 정렬된다.

## 4. 저장 · 부수효과 (집계)

### BR-14
- 규칙: 없는 학급이면 오류 없이 빈 결과를 돌려주고 아무것도 저장하지 않는다.
- 근거: `legacy/grade-mssql/sql/usp_aggregate_grades.sql:43-52`
- 근거 코드:
    ```sql
    IF @class_name IS NULL
    ...
         WHERE 1 = 0;
        RETURN;
    ```
- 확신도: 확실
- 비고: `REP` 는 학급 존재가 아니라 재적 학생 수가 0 인지로 판정한다(`legacy/grade-mssql/sql/usp_class_report.sql:37`). 그래서 학급은 있고 학생이 0 명이면 동작이 다르다. `AGG` 는 이 경우 계속 진행해 BR-15 의 DELETE 로 그 학급의 `grade_summary` 행을 모두 지운다(코드를 읽어 따라간 것이고 실행하지는 않았다).

### BR-15
- 규칙: 집계하면 `grade_summary` 에서 (학생, 단원)이 같은 행은 갱신하고, 없으면 추가하고, 이번 집계에 없는 그 학급 행은 삭제한다.
- 근거: `legacy/grade-mssql/sql/usp_aggregate_grades.sql:333-344`, `:347-356`, `:359-367`
- 근거 코드:
    ```sql
    DELETE gs
      FROM dbo.grade_summary AS gs
     WHERE gs.class_id = @class_id
    ```
- 확신도: 확실
- 비고: PK 가 `(student_id, unit_code)` 이고 `class_id` 가 없다(`db/mssql/init/01-schema.sql:105`). 그래서 반을 옮긴 학생은 새 학급으로 집계할 때 `class_id` 가 덮어써진다(`:334`). 보너스 단원 미제출 행과 전출 학생 행이 지워진다는 설명은 주석(`:358`)에 있다.

### BR-16
- 규칙: 저장 후 계산한 행마다 `grade_summary.score` 와 비교해 하나라도 다르거나 없으면 전체를 롤백하고 오류를 낸다.
- 근거: `legacy/grade-mssql/sql/usp_aggregate_grades.sql:390-391`, `:401-405`
- 근거 코드:
    ```sql
        IF @saved IS NULL OR @saved <> @adj
            SET @mismatch = @mismatch + 1;
    ```
- 확신도: 확실
- 비고: **주석과 코드가 다르다.** 주석(`:369`)은 "저장된 행 수와 계산한 행 수가 같은지"라고 하지만, 코드는 행 수가 아니라 행마다 점수 값을 대조한다. 행 수는 `@row_count` 로 세기만 하고(`:393`) 어디서도 비교하지 않는다(선언 `:34` 외 사용처 없음). `grade_summary` 에 남은 여분의 행은 잡지 않는다. 오류가 나면 컨트롤러가 502 화면을 보여 준다(BR-26).

### BR-17
- 규칙: 집계에 성공하면 그 학급 학생의 모든 제출 행(`X` 포함)에 `aggregated_at` 을 기록한다.
- 근거: `legacy/grade-mssql/sql/usp_aggregate_grades.sql:409-413`
- 근거 코드:
    ```sql
    UPDATE sub
       SET sub.aggregated_at = @now
    ```
- 확신도: 확실
- 비고: `status` 조건이 없어서 무효 제출에도 시각이 찍힌다. 현재 시각을 `SYSDATETIME()` 으로 얻는다(`:30`).

## 5. 학급 보고

### BR-18
- 규칙: 가중치가 0 이 아닌 단원의 평균은 미제출을 0점으로 넣어 (유효 점수 합 ÷ 재적 인원)을 소수 둘째 자리로 반올림한다.
- 근거: `legacy/grade-mssql/sql/usp_class_report.sql:125-126`
- 근거 코드:
    ```sql
               -- 일반 단원: 미제출 0점 포함, 재적 인원으로 나눈다
               ELSE ROUND(ISNULL(e.sum_score, 0.0) / @enrolled, 2)
    ```
- 확신도: 확실
- 비고: 매직 넘버 `2`(자릿수). 집계 쪽 점수는 첫째 자리인데 보고 평균만 둘째 자리다. 이 차이는 주석(`:18`)에도 적혀 있다.

### BR-19
- 규칙: 보너스 단원(가중치 `0.00`)의 평균은 제출한 학생만으로 구하고, 제출자가 없으면 NULL 로 한다.
- 근거: `legacy/grade-mssql/sql/usp_class_report.sql:122-124`
- 근거 코드:
    ```sql
               WHEN u.weight = 0.00 THEN
                   CASE WHEN ISNULL(e.submitted, 0) = 0 THEN NULL
                        ELSE ROUND(e.sum_score / e.submitted, 2) END
    ```
- 확신도: 확실
- 비고: 보고는 `unit.weight` 를 쓰고, 집계 종합(BR-10)은 하드코딩한 가중치를 쓴다.

### BR-20
- 규칙: 가중치가 0 이 아닌 단원에 미제출 학생이 있으면 최저 점수를 0.0 으로 하고, 그 밖에는 유효 제출 중 최저 점수를 쓴다. 최고 점수는 유효 제출 중 최고다.
- 근거: `legacy/grade-mssql/sql/usp_class_report.sql:128-133`
- 근거 코드:
    ```sql
               WHEN u.weight = 0.00 THEN e.min_score
               WHEN ISNULL(e.submitted, 0) < @enrolled THEN 0.0     -- 미제출이 있으면 최저는 0
               ELSE e.min_score
    ```
- 확신도: 확실
- 비고: 아무도 내지 않은 단원의 최고 점수는 `LEFT JOIN` 결과라 NULL 이다(`:128`, `:135-145`).

### BR-21
- 규칙: 단원별 제출 수는 유효 제출 건수, 미제출 수는 (재적 인원 − 제출 수), 지연 수는 유효 제출 중 지연 건수로 한다.
- 근거: `legacy/grade-mssql/sql/usp_class_report.sql:116-118`, `:136-143`
- 근거 코드:
    ```sql
           ISNULL(e.submitted, 0),
           @enrolled - ISNULL(e.submitted, 0),
           ISNULL(e.late, 0),
    ```
- 확신도: 확실
- 비고: **같은 규칙이 다르게 구현돼 있다.** 보고는 `submission.unit_code` 로 단원을 묶는다(`:77`, `:143`). 집계는 과제(`assignment_id`)로 찾은 뒤 `#unit` 의 단원 코드를 쓴다(`legacy/grade-mssql/sql/usp_aggregate_grades.sql:182`, `:218`). 두 값이 어긋난 제출이 있으면 두 화면의 단원별 결과가 달라진다. 한 단원에 과제가 여러 개면 제출 수가 재적 인원보다 커져 미제출 수가 음수가 될 수 있다(실행해 보지 않음).

### BR-22
- 규칙: 단원별 제외 수는 그 학급 학생의 `status = 'X'` 제출 **행 수**로 한다.
- 근거: `legacy/grade-mssql/sql/usp_class_report.sql:146-155`
- 근거 코드:
    ```sql
            SELECT sub.unit_code, COUNT(*) AS excluded
             WHERE s.class_id = @class_id
               AND sub.status = 'X'
    ```
- 확신도: 확실
- 비고: 학생 수가 아니라 행 수라서, 한 학생이 무효 제출을 여러 번 하면 여러 번 센다. 재제출 정리(BR-02)도 적용하지 않는다.

### BR-23
- 규칙: 학급 보고를 조회하면 그 학급 학생의 모든 제출 행에 `reported_at` 을, 그 학급의 `grade_summary` 행에 `last_report_at` 을 기록한다.
- 근거: `legacy/grade-mssql/sql/usp_class_report.sql:164-175`
- 근거 코드:
    ```sql
       SET sub.reported_at = @now
       SET gs.last_report_at = @now
    ```
- 확신도: 확실
- 비고: 조회 화면(GET)인데 쓰기가 일어난다. 잠금 순서가 `AGG` 와 반대다(`:158-159`, `legacy/grade-mssql/sql/usp_aggregate_grades.sql:327-328`).

## 6. 호출부(Java)

### BR-24
- 규칙: `class_id` 가 없거나 공백이면 `C1` 을 쓰고, 값이 있으면 앞뒤 공백을 지운다.
- 근거: `legacy/grade-mssql/src/main/java/com/example/grade/GradeController.java:23`, `:83-88`
- 근거 코드:
    ```java
            if (classId == null || classId.isBlank()) {
                return DEFAULT_CLASS;
            }
    ```
- 확신도: 확실
- 비고: 매직 값 `C1`(상수로 이름은 있음). 대소문자는 바꾸지 않는다.

### BR-25
- 규칙: 결과 값이 NULL 이면 빈 문자열로, DECIMAL 이면 자릿수 그대로(`87.5`, `78.75`) 표시하고, 나머지는 앞뒤 공백을 지워 표시한다.
- 근거: `legacy/grade-mssql/src/main/java/com/example/grade/GradeRepository.java:45-53`
- 근거 코드:
    ```java
            if (v instanceof BigDecimal d) {
                return d.toPlainString();
            }
    ```
- 확신도: 확실
- 비고: 그래서 집계 점수는 소수 한 자리, 보고 평균은 두 자리로 화면에 그대로 나온다(BR-18). 이관 후 JSON 비교에 영향이 있다.

### BR-26
- 규칙: DB 오류가 나면 HTTP 502 로 응답하고, 가장 안쪽 원인 예외의 메시지를 화면에 보여 준다.
- 근거: `legacy/grade-mssql/src/main/java/com/example/grade/GradeController.java:57-58`, `:108-113`
- 근거 코드:
    ```java
            String msg = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage();
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).contentType(MediaType.parseMediaType(HTML)).body(body);
    ```
- 확신도: 확실
- 비고: BR-16 의 RAISERROR 메시지(학급 ID 포함)가 그대로 화면에 나간다.

## 7. 자릿수

### BR-27
- 규칙: 단원 점수(원점수 · 감점 후 점수)와 보고의 최고 · 최저 점수는 소수 첫째 자리(`DECIMAL(5,1)`)로, 보고의 평균은 소수 둘째 자리(`DECIMAL(5,2)`)로 다룬다.
- 근거: `legacy/grade-mssql/sql/usp_aggregate_grades.sql:85-86`, `legacy/grade-mssql/sql/usp_class_report.sql:107-109`
- 근거 코드:
    ```sql
        adj_score    DECIMAL(5,1) NOT NULL,
        avg_score   DECIMAL(5,2)  NULL,
        max_score   DECIMAL(5,1)  NULL,
    ```
- 확신도: 확실
- 비고: 교차 검증(CX-12 · CX-14)에서 추가했다. BR-25 의 `toPlainString` 때문에 이 자릿수가 화면에 그대로 나온다(`87.5`, `78.75`). 원천 `submission.score` 도 `DECIMAL(5,1)` 이다(`db/mssql/init/01-schema.sql:77`).

## 매직 넘버

| 값 | 위치 | 추정 의미 |
|---|---|---|
| `'X'` | `legacy/grade-mssql/sql/usp_aggregate_grades.sql:130`, `legacy/grade-mssql/sql/usp_class_report.sql:89`, `:152` | 무효 제출 상태 |
| `2` (DAY) | `legacy/grade-mssql/sql/usp_aggregate_grades.sql:209`, `legacy/grade-mssql/sql/usp_class_report.sql:70`, `:74` | 지연 유예 기간(일) |
| `0.9` | `legacy/grade-mssql/sql/usp_aggregate_grades.sql:213`, `legacy/grade-mssql/sql/usp_class_report.sql:71` | 지연 감점 후 반영 비율(10% 감점) |
| `1` (ROUND 자릿수) | `legacy/grade-mssql/sql/usp_aggregate_grades.sql:213`, `:308`, `:312-315`, `legacy/grade-mssql/sql/usp_class_report.sql:71` | 점수 · 종합 소수 첫째 자리 |
| `2` (ROUND 자릿수) | `legacy/grade-mssql/sql/usp_class_report.sql:124`, `:126` | 보고 평균 소수 둘째 자리 |
| `90.0 / 80.0 / 70.0 / 60.0` | `legacy/grade-mssql/sql/usp_aggregate_grades.sql:238-241`, `:312-315` | 등급 A/B/C/D 하한 |
| `0.30 / 0.25 / 0.25 / 0.20 / 0.00` | `legacy/grade-mssql/sql/usp_aggregate_grades.sql:282-287` | 단원 `M5-1 / M5-2 / M5-3 / M6-1 / M6-2` 종합 가중치 (`unit.weight` 와 중복) |
| `0.00` (weight) | `legacy/grade-mssql/sql/usp_aggregate_grades.sql:189`, `legacy/grade-mssql/sql/usp_class_report.sql:122`, `:130` | 보너스 단원 표시 |
| `0.0` | `legacy/grade-mssql/sql/usp_aggregate_grades.sql:197`, `:203`, `legacy/grade-mssql/sql/usp_class_report.sql:71-72`, `:131` | 미제출 · 점수 없음의 점수 |
| `'TOTAL'` | `legacy/grade-mssql/sql/usp_aggregate_grades.sql:306`, `:429` | 학생 종합 행의 단원 코드 |
| `'C1'` | `legacy/grade-mssql/src/main/java/com/example/grade/GradeController.java:23` | 기본 학급 |

## 같은 규칙이 두 곳에 다르게 있는 곳 (요약)

| 주제 | 집계 `AGG` | 보고 `REP` |
|---|---|---|
| 가중치 출처 | 미제출 판정은 `unit.weight`(`:105`), 종합은 하드코딩(`:281-288`) | `unit.weight`(`:115`, `:122`) |
| 단원 묶는 키 | 과제로 찾고 `#unit` 의 단원(`:182`, `:218`) | `submission.unit_code`(`:77`, `:143`) |
| 빈 학급 판정 | `class` 존재 여부(`:43`) | 재적 학생 0명(`:37`) |
| 평균 · 점수 자릿수 | 첫째 자리(`:213`, `:308`) | 평균 둘째 자리(`:124`, `:126`) |
| 잠금 순서 | grade_summary → submission(`:327-328`) | submission → grade_summary(`:158-159`) |

## 주석만 있는 것 (규칙으로 올리지 않음)

- 단원 가중치 합계는 1.00 — `db/mssql/init/01-schema.sql:41`
- 과제는 단원당 1개 — `db/mssql/init/01-schema.sql:52`
- 제출 상태 `S=제출` — `db/mssql/init/01-schema.sql:65` (코드는 `S` 를 비교하지 않고 `X` 만 본다)
- "아래 CASE 문은 아직 정리 안 됨" — `legacy/grade-mssql/sql/usp_aggregate_grades.sql:16`

## 검증 이력

| 날짜 | 규칙 | 뽑은 기준 | 판정 | 조치 |
|---|---|---|---|---|
| 2026-09-29 | BR-09 | 가장 그럴듯하고 깔끔한 규칙 | 통과 — `AGG:236-243` 에 `>= 90.0 / 80.0 / 70.0 / 60.0`, `ELSE 'F'` 그대로 있음 | 없음 |
| 2026-09-29 | BR-16 | 주석과 코드의 불일치 | 통과 — `AGG:369` 주석은 "행 수", `AGG:390-391` 코드는 점수 값 대조 | 비고 보완: `@row_count` 는 세기만 하고 비교하지 않음(`AGG:34`, `:393`) |
| 2026-09-29 | BR-26 | 맨 뒤 규칙 | 통과 — `GradeController.java:57-58`, `:108-113` 에 `getMostSpecificCause`, `BAD_GATEWAY` 있음 | 없음 |
| 2026-09-29 | BR-01 | 맨 앞 규칙(추가) | 통과 — `AGG:130`, `REP:89` 에 `status <> 'X'` 있음 | 인용 출처 표기 보완: 인용은 `AGG:130`, `REP:89` 는 `AND ...` 로 시작 |
| 2026-09-29 | BR-13 | 가운데 규칙(추가) | 통과 — `AGG:428-430` 정렬식 그대로 있음 | 없음 |
| 2026-09-29 | 전체 | 이름 존재 확인 | 통과 — `usp_aggregate_grades` · `usp_class_report` · `grade_summary` · `cur_verify` · `@mismatch` · `@row_count` · `getMostSpecificCause` · `BAD_GATEWAY` · `'TOTAL'` 모두 `grep` 1건 이상 | 없음 |
| 2026-09-29 | BR-10 ↔ CX-13 | 교차 검증: 내용 다름 | BUSINESS-RULES 쪽이 맞음 — 단원 점수는 `DECIMAL(5,1)` 로 이미 첫째 자리(`AGG:86`, `:250`). CX-13 의 "단원 점수를 먼저 반올림하지 않고 합산"은 틀림. 반올림하지 않는 것은 `단원 점수 × 가중치` 곱(`AGG:249`, `:290`) | BR-10 비고 보완: 곱은 `DECIMAL(9,4)` 로 누적 후 한 번만 반올림 |
| 2026-09-29 | BR-06 ↔ CX-09 | 교차 검증: 내용 다름 | 둘 다 코드와 맞음 — `AGG:208` 에 NULL 확인이 있으나 `due_at` 은 NOT NULL(`01-schema.sql:60`)이라 도달하지 않는 경로. CX-09 는 규칙으로, BR-06 은 비고로 적었을 뿐 | 없음 |
| 2026-09-29 | BR-21 ↔ CX-20 | 교차 검증: 내용 다름 | BUSINESS-RULES 쪽이 더 정확 — `submitted` 는 `#eff`(PK 학생·과제, `REP:62`)의 행 수(`REP:137`). CX-20 의 "제출 인원"은 단원당 과제 1개일 때만 같음 | 없음 |
| 2026-09-29 | BR-03 | 교차 검증: BUSINESS-RULES 에만 있음 | 코드와 맞음 — `AGG:104-109` 단원 적재에 학급 조건 없음. CX 가 놓침 | 없음 |
| 2026-09-29 | BR-17 | 교차 검증: BUSINESS-RULES 에만 있음 | 코드와 맞음 — `AGG:409-413` 에 status 조건 없음. CX 범위(제외 · 반올림 · 미제출 · 가중치) 밖 | 없음 |
| 2026-09-29 | BR-27 (CX-12 · CX-14) | 교차 검증: CROSS-CHECK 에만 있음 | 코드와 맞음 — `AGG:85-86`, `REP:107-109` | BR-27 추가 |
