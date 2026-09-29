# 성적 집계 비즈니스 규칙 (교차 검증용, 독립 추출)

- 근거: `legacy/grade-mssql/` 의 코드만 읽고 추출했다. 줄번호는 `cat -n` 으로 확인한 값이다.
- 약칭: AGG = `legacy/grade-mssql/sql/usp_aggregate_grades.sql`, REP = `legacy/grade-mssql/sql/usp_class_report.sql`, REPO = `legacy/grade-mssql/src/main/java/com/example/grade/GradeRepository.java`

## 1. 집계 대상 제외 조건

### CX-01
- 규칙: 제출 상태(status)가 `'X'` 인 제출은 집계에서 제외한다. 제외 조건은 이것 하나뿐이다.
- 근거: AGG:130
```
             WHERE sub.status <> 'X'
```
- 주석(AGG:114): "제외 조건은 status = 'X' 하나뿐이다" — 코드와 일치.

### CX-02
- 규칙: 같은 학생 · 같은 과제의 제출이 여럿이면 `submitted_at` 이 가장 늦은 1건만 인정하고, 시각이 같으면 `id` 가 큰 것을 인정한다.
- 근거: AGG:123-125, AGG:132
```
                   ROW_NUMBER() OVER (
                       PARTITION BY sub.student_id, sub.assignment_id
                       ORDER BY sub.submitted_at DESC, sub.id DESC
```
- 제외(X) 처리가 순위 매기기 전에(WHERE) 적용되므로, 최신 제출이 X 이면 그 이전 제출이 인정된다.

### CX-03
- 규칙: 점수가 NULL 인 제출은 제외하지 않고 0점으로 집계한다.
- 근거: AGG:202-203
```
                IF @raw IS NULL
                    SET @raw = 0.0;
```
- 학급 보고서에서도 같다: REP:71-72 `ISNULL(v.score, 0.0)`.

### CX-04
- 규칙: 집계 대상 학생은 요청 학급(`class_id`)에 속한 학생이고, 없는 학급이면 오류 없이 빈 결과를 돌려준다.
- 근거: AGG:43, AGG:99
```
    IF @class_name IS NULL
     WHERE s.class_id = @class_id
```

### CX-05
- 규칙: 학급 보고서는 재적 인원이 0 이면 빈 결과를 돌려준다.
- 근거: REP:37
```
    IF @enrolled IS NULL OR @enrolled = 0
```

### CX-06
- 규칙: 학급 보고서의 `excluded` 는 학급 학생의 status `'X'` 제출 행 수를 단원별로 센 값이다(학생·과제 중복 제거 없이 행 단위).
- 근거: REP:147-153
```
    SELECT sub.unit_code, COUNT(*) AS excluded
     WHERE s.class_id = @class_id
       AND sub.status = 'X'
```

### CX-07
- 규칙: 학급 보고서도 집계와 같은 제외(status 'X') · 최신 1건 규칙을 별도로 다시 구현한다(공유 코드 없음).
- 근거: REP:83, REP:89
```
                       ORDER BY sub.submitted_at DESC, sub.id DESC
               AND sub.status <> 'X'
```
- 주석(REP:13): "집계 규칙은 usp_aggregate_grades 와 같아야 하지만 별도로 구현되어 있다."

## 2. 지연 제출 감점

### CX-08
- 규칙: 제출 시각이 마감(`due_at`) + 2일을 초과하면 지연으로 보고 점수에 0.9 를 곱한다(10% 감점). 정확히 2일째까지는 감점하지 않는다.
- 근거: AGG:208-213
```
                IF @due_at IS NOT NULL
                   AND @submitted_at > DATEADD(DAY, 2, @due_at)
                    SET @adj = ROUND(@raw * 0.9, 1);
```

### CX-09
- 규칙: 마감(`due_at`)이 NULL 인 과제는 지연으로 판정하지 않는다(집계). 보고서도 NULL 과의 비교가 참이 되지 않아 지연 아님으로 처리된다.
- 근거: AGG:208 / REP:70, REP:74
```
                IF @due_at IS NOT NULL
               WHEN v.submitted_at > DATEADD(DAY, 2, a.due_at)
```
- 보고서의 NULL 처리는 명시 조건이 아니라 SQL 비교 의미에 따른 결과다.

### CX-10
- 규칙: 학급 보고서의 `late` 는 지연 제출 건수이며, 평균 · 최대 · 최소는 감점 적용 점수(`eff_score`)로 계산한다.
- 근거: REP:69-74, REP:138-141
```
                   THEN ROUND(ISNULL(v.score, 0.0) * 0.9, 1)     -- 지연 10% 감점
                   SUM(CAST(is_late AS INT)) AS late,
                   SUM(eff_score)           AS sum_score,
```

## 3. 반올림과 자릿수

### CX-11
- 규칙: 지연 감점 점수는 소수 첫째 자리로 `ROUND` 한다(SQL Server ROUND 는 0.5 를 올림, 즉 0 에서 먼 쪽).
- 근거: AGG:212-213
```
                    -- 반올림: 소수 첫째 자리, 5 는 올림 (ROUND 는 0.5 를 올린다)
                    SET @adj = ROUND(@raw * 0.9, 1);
```
- 주석의 "5 는 올림" 은 코드의 `ROUND` 동작과 일치한다(반올림 방식을 코드가 따로 지정하지는 않음).

### CX-12
- 규칙: 단원 점수는 `DECIMAL(5,1)`(소수 첫째 자리)로 저장한다.
- 근거: AGG:85-86
```
        raw_score    DECIMAL(5,1) NULL,
        adj_score    DECIMAL(5,1) NOT NULL,
```

### CX-13
- 규칙: 학생별 종합(TOTAL) 점수는 `Σ(단원 점수 × 가중치)` 를 `DECIMAL(9,4)` 로 누적한 뒤 소수 첫째 자리로 `ROUND` 한다. 단원 점수를 먼저 반올림하지 않고 합산한다.
- 근거: AGG:249, AGG:290, AGG:308
```
    DECLARE @total       DECIMAL(9,4);
            SET @total = @total + (@unit_score * @w);
            ROUND(@total, 1),
```

### CX-14
- 규칙: 학급 보고서의 평균(`avg_score`)은 소수 둘째 자리(`DECIMAL(5,2)`, `ROUND(…, 2)`), 최대 · 최소는 소수 첫째 자리(`DECIMAL(5,1)`)다.
- 근거: REP:107-109, REP:126
```
        avg_score   DECIMAL(5,2)  NULL,
        max_score   DECIMAL(5,1)  NULL,
               ELSE ROUND(ISNULL(e.sum_score, 0.0) / @enrolled, 2)
```
- 주석(REP:18): "평균은 소수 둘째 자리 (집계 프로시저는 첫째 자리)" — 코드와 일치.

### CX-15
- 규칙: 화면 표시 시 DECIMAL 값은 자릿수 그대로(`toPlainString`) 문자열로 만들고, NULL 은 빈 문자열로 표시한다.
- 근거: REPO:44-51 (주석 REPO:44 포함)
```
        if (v == null) {
            return "";
        if (v instanceof BigDecimal d) {
            return d.toPlainString();
```

## 4. 등급

### CX-16
- 규칙: 단원 등급은 점수가 90 이상 A, 80 이상 B, 70 이상 C, 60 이상 D, 그 미만 F 다. 감점 후 점수(`adj_score`) 기준이다.
- 근거: AGG:238-242
```
                       WHEN adj_score >= 90.0 THEN 'A'
                       WHEN adj_score >= 80.0 THEN 'B'
                       ELSE 'F'
```

### CX-17
- 규칙: TOTAL 등급은 반올림된 종합 점수(`ROUND(@total, 1)`)에 같은 90/80/70/60 기준을 적용한다.
- 근거: AGG:311-316
```
            CASE
                WHEN ROUND(@total, 1) >= 90.0 THEN 'A'
                WHEN ROUND(@total, 1) >= 60.0 THEN 'D'
```

## 5. 미제출 처리

### CX-18
- 규칙: 유효 제출이 없으면 미제출로 표시(`is_missing = 1`)하고, 가중치가 0 이 아닌 단원은 0점으로 집계한다.
- 근거: AGG:184-187, AGG:196-197
```
            IF @found = 0
                SET @is_missing = 1;
                SET @adj = 0.0;
```

### CX-19
- 규칙: 가중치가 0.00 인 단원(보너스)을 미제출하면 해당 학생 · 단원 행 자체를 만들지 않는다(0점 행 없음, 종합에도 포함되지 않음).
- 근거: AGG:189-193
```
                IF @unit_weight = 0.00
                    -- 보너스 단원은 미제출이면 행 자체를 만들지 않는다 (2023-11)
                    CONTINUE;
```
- 저장 단계에서도 이번 집계에 없는 학급 행은 `grade_summary` 에서 삭제된다(AGG:359-367).

### CX-20
- 규칙: 학급 보고서에서 `missing` = 재적 인원 − 제출 인원이며, 보너스 단원에도 같은 식이 적용된다.
- 근거: REP:117
```
           @enrolled - ISNULL(e.submitted, 0),
```

### CX-21
- 규칙: 학급 보고서 평균은 가중치가 있는 단원이면 미제출을 0점으로 포함해 재적 인원으로 나누고, 보너스 단원이면 제출자 수로 나누며 제출자가 없으면 NULL 이다.
- 근거: REP:122-126
```
               WHEN u.weight = 0.00 THEN
                   CASE WHEN ISNULL(e.submitted, 0) = 0 THEN NULL
                        ELSE ROUND(e.sum_score / e.submitted, 2) END
               ELSE ROUND(ISNULL(e.sum_score, 0.0) / @enrolled, 2)
```

### CX-22
- 규칙: 학급 보고서 최저점은 가중치가 있는 단원에서 미제출자가 있으면 0.0 이고, 보너스 단원이거나 전원이 제출했으면 제출 점수의 최솟값이다. 최고점은 제출 점수의 최댓값이다(미제출 0 을 반영하지 않음).
- 근거: REP:129-133, REP:140
```
               WHEN u.weight = 0.00 THEN e.min_score
               WHEN ISNULL(e.submitted, 0) < @enrolled THEN 0.0     -- 미제출이 있으면 최저는 0
                   MAX(eff_score)           AS max_score,
```

### CX-23
- 규칙: 단원에 과제가 없거나 과제에 대한 유효 제출을 찾지 못하면 미제출 경로를 탄다(단원 × 과제를 LEFT JOIN 으로 만들어 과제 ID 로 유효 제출을 찾음).
- 근거: AGG:107-108, AGG:181-182
```
      LEFT JOIN dbo.assignment AS a
        ON a.unit_code = u.code
               AND v.assignment_id = @assignment_id;
```
- 단원에 과제가 여러 개면 같은 (학생, 단원)에 `#calc` 행이 여러 개 생겨 PK(AGG:90)와 충돌할 수 있으나, 코드에서 이를 처리하는 부분은 없다(관찰만, 규칙 아님).

### CX-24
- 규칙: 종합(TOTAL) 행의 지연 · 미제출 플래그는 단원 행 중 하나라도 해당하면 1 이다.
- 근거: AGG:292-295
```
            IF @is_late = 1
                SET @has_late = 1;
            IF @is_missing = 1
                SET @has_missing = 1;
```

## 6. 단원별 가중치

### CX-25
- 규칙: 종합 점수에 쓰는 가중치는 프로시저 안 하드코딩된 CASE 값이다: M5-1 0.30, M5-2 0.25, M5-3 0.25, M6-1 0.20, M6-2 0.00, 그 외 단원은 0.00. `unit.weight` 컬럼은 종합 계산에 쓰지 않는다.
- 근거: AGG:281-288
```
            SET @w = CASE @unit_code
                         WHEN 'M5-1' THEN 0.30
                         WHEN 'M6-1' THEN 0.20
                         ELSE 0.00
```
- 주석(AGG:280): "TODO: unit.weight 를 쓰도록 바꾸기 (2022-02 이후 값이 두 곳에 있음)". 주석(AGG:16)도 "아래 CASE 문은 아직 정리 안 됨". 코드 기준으로는 CASE 값이 적용된다.

### CX-26
- 규칙: `unit.weight` 컬럼 값은 "미제출 시 행 생성 여부(0.00 인가)" 판정에만 쓴다(집계). 학급 보고서는 `u.weight = 0.00` 여부로 평균 · 최저 방식을 결정한다.
- 근거: AGG:67, AGG:189 / REP:122
```
        weight      DECIMAL(3,2) NOT NULL,     -- unit 테이블 값 (미제출 판정에만 사용)
                IF @unit_weight = 0.00
               WHEN u.weight = 0.00 THEN
```
- 가중치 합은 0.30+0.25+0.25+0.20 = 1.00 이어서 종합 점수를 가중치 합으로 나누지 않는다(코드에 나누는 부분 없음).

### CX-27
- 규칙: 가중치 0 단원(M6-2, 보너스)은 종합 점수에 기여하지 않는다(가중치 0 을 곱함).
- 근거: AGG:285-286, AGG:290
```
                         WHEN 'M6-2' THEN 0.00
            SET @total = @total + (@unit_score * @w);
```

## 7. 저장 · 부수효과 (참고)

### CX-28
- 규칙: 집계 결과는 `grade_summary` 에 UPSERT(있으면 갱신, 없으면 추가)하고, 저장 후 점수가 계산 값과 다르거나 없으면 전체를 롤백하고 오류를 낸다.
- 근거: AGG:390-393, AGG:401-405
```
        IF @saved IS NULL OR @saved <> @adj
            SET @mismatch = @mismatch + 1;
        ROLLBACK TRANSACTION;
```
- 주석(AGG:369): "저장된 행 수와 계산한 행 수가 같은지" — 실제 코드는 행 수가 아니라 행마다 점수(`gs.score` 와 `adj_score`)를 비교한다. 코드 기준으로 위와 같다.

### CX-29
- 규칙: 결과 정렬은 학생 ID 순이고, 학생 안에서 단원 행을 단원 코드 순으로 먼저, TOTAL 행을 마지막에 둔다.
- 근거: AGG:428-430
```
     ORDER BY c.student_id,
              CASE WHEN c.unit_code = 'TOTAL' THEN 1 ELSE 0 END,
              c.unit_code;
```
