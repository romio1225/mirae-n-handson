# 검증 리포트: docs/grade/BUSINESS-RULES.md

- 검증 대상: `docs/grade/BUSINESS-RULES.md` (433줄). 요청 범위: "레거시 grade 모듈의 비즈니스 규칙을 근거 파일:줄번호와 함께 문서화"
- 검증 방식: 새 리뷰 세션. 문서가 인용한 `legacy/grade-mssql/` 의 SQL · Java · properties, `db/mssql/init/01-schema.sql` · `02-seed.sql`, `modern/api` 의 인용 줄을 `cat -n` 으로 전수 대조. 코드 · 문서는 수정하지 않음(`legacy/` 읽기만).
- 기준: 이번 리뷰 전용 4개 기준(요청 프롬프트). 코드용 `templates/approval-checklist.md` 의 테스트 · 명령 항목은 해당 없음(아래 메모).

## 판정: 반려

BR-01 ~ BR-28 의 근거는 전부 존재하고 규칙을 뒷받침한다. 반려 사유는 BR 규칙 밖, 문서 뒤쪽 "이관 대조" 표의 인용 1건(`REPO:28-29`)이 규칙 설명을 뒷받침하지 않기 때문이다(이슈 #1). 이 1건을 고치면 승인 가능하다.

## 기준별 결과

| 기준 | 등급 | 결과 | 근거 |
|---|---|---|---|
| 모든 BR-xx 에 근거 `파일:줄번호` 1개 이상 | 반려 | 충족 | BR-01 ~ BR-28 전부 `근거:` 에 파일:줄번호 있음(28/28) |
| 인용 근거가 존재하고 규칙을 뒷받침 | 반려 | **미충족** | BR 28건은 전부 통과. 이관 대조 표 `REPO:28-29` 가 어긋남(#1) |
| 약칭이 문서 안에 정의됨 | 경고 | 미충족 | `CX-nn` · `CROSS-CHECK` 미정의(#2), 파일 없는 `:NN` 줄번호(#3) |
| 규칙 문장이 예/아니오로 확인 가능 | 경고 | 일부 미충족 | BR-04 · BR-27 의 "처리한다" · "다룬다"(#4) |

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 치명 (기준 2, [반려]) | `docs/grade/BUSINESS-RULES.md:418` (이관 대조 표 "단원 순서" 행의 `REPO:28-29`) | `REPO` = `GradeReportRepository.java`(문서 404줄 정의). 그 파일 28-29줄은 `COUNT_ENROLLED_SQL`(재적 수 조회)이고, "DB 에서 ORDER BY"인 단원 조회는 `modern/api/src/main/java/com/example/grade/GradeReportRepository.java:38-40`(`UNITS_SQL … ORDER BY u.code`). 규칙(단원 순서)을 뒷받침하지 않음 | `REPO:28-29` 를 `REPO:38-40` 으로 바꾼다 |
| 2 | 경고 (기준 3) | `docs/grade/BUSINESS-RULES.md:336`, `:393-398` | `CX-12` · `CX-13` · `CX-14` · `CX-09` · `CX-20` · `CROSS-CHECK` 가 이 문서에서 정의되지 않음(정의는 `docs/grade/CROSS-CHECK.md:8` 이하의 별도 문서). 또 `REPO` 가 이 문서(modern `GradeReportRepository`)와 `CROSS-CHECK.md:3`(legacy `GradeRepository`)에서 서로 다른 파일을 뜻함 | 약칭 줄(7줄)에 `CX-nn` = `docs/grade/CROSS-CHECK.md` 의 규칙 번호를 추가하고, 이 문서의 `REPO` 를 `GRR` 등으로 바꾸거나 파일명을 쓴다 |
| 3 | 경고 (기준 3) | `docs/grade/BUSINESS-RULES.md:54, 67, 79, 91, 103, 129, 152, 202, 226, 285` 등 비고 | `:90`, `:114`, `:212`, `:105`, `:161`, `:17`, `:280`, `:420-424` 처럼 파일 없는 줄번호가 다수. 읽는 쪽이 AGG 인지 REP 인지 추측해야 하고, 7줄 약칭 정의의 "근거 칸에는 전체 경로" 규칙은 비고에 적용되지 않음 | 비고의 줄번호도 `AGG:90` · `REP:114` 형식으로 적는다 |
| 4 | 경고 (기준 4) | `docs/grade/BUSINESS-RULES.md:46-47` (BR-04), `:327` (BR-27) | BR-04 "미제출로 처리한다": 점수 0 · `is_missing=1` · 보너스 단원은 행 생략(BR-08)까지 한 문장에 담겨 결과가 불명확. BR-27 "다룬다": 저장 · 반올림 · 표시 중 무엇인지 불명확 | BR-04 는 "행을 `found=0` 으로 보고 BR-07 · BR-08 을 적용한다"처럼 결과로 쓴다. BR-27 은 "…열의 타입이 DECIMAL(5,1)/(5,2) 다"로 바꾼다 |
| 5 | 경고 (기준 2 보조) | `docs/grade/BUSINESS-RULES.md:347` (BR-28 확신도) | "11자 값과 `c1` 이 C1 과 같은 5건" 은 재현되지 않음. `characterization/__snapshots__/grade.test.js.snap` 에서 정상 C1 과 동일한 스냅샷은 6건(11자+공백, `c1`, 앞뒤 공백, 누락, 빈값, 공백만)이고 BR-28 에 해당하는 것은 2건. 테스트 제목은 `[BR-24]` 로 태그됨(`characterization/tests/grade.test.js:41`, `:45`). 파일만 있고 줄번호 없음 | "11자 값 · 소문자 `c1` 2건이 C1 스냅샷과 동일(`grade.test.js:41`, `:45`)" 로 고친다 |
| 6 | 경고 (기준 2, 줄번호 어긋남) | `docs/grade/BUSINESS-RULES.md:411`, `:413` | `SVC:179`(`sqlKey`)는 javadoc 끝 `*/` 이고 메서드는 `GradeReportService.java:180`. BR-21 의 `SVC:103` 은 지연 계산이고 단원 키 `sqlKey(row.unitCode())` 는 `:104`. 한 줄씩 어긋나 뒷받침은 인접 줄에서 확인됨 | `SVC:180`, `SVC:104` 로 고친다 |
| 7 | 제안 | `docs/grade/BUSINESS-RULES.md:402-433` | 요청은 "레거시 규칙 문서화"인데 modern 이관 대조 · 회고가 같은 문서에 있어 범위가 넓음. 위 #1 · #6 이 모두 이 구간에서 나옴 | 별도 문서로 분리하거나, 요청 범위에 포함됐다는 근거를 PR 설명에 남긴다 |
| 8 | 제안 | BR-04 `:54`, BR-14 `:179`, BR-21 `:262`, BR-26 `:322` | 비고에 "실행해 보지 않음 · 읽어 따라간 것"이라 썼는데 확신도는 "확실". 문서 제목도 "규칙 후보" | 미실행 추론이 든 규칙은 확신도를 "코드 판독(미실행)"으로 구분한다 |

## 규칙별 근거 확인 표

존재 = 파일이 있고 줄 범위 안. 뒷받침 = 그 줄 내용이 규칙을 확인해 준다. 확인한 줄은 `cat -n` 기준.
AGG = `legacy/grade-mssql/sql/usp_aggregate_grades.sql`, REP = `…/sql/usp_class_report.sql`, GC = `…/src/main/java/com/example/grade/GradeController.java`, GR = `…/GradeRepository.java`, SCH = `db/mssql/init/01-schema.sql`, SEED = `db/mssql/init/02-seed.sql`.

| BR | 인용 근거 | 존재 | 뒷받침 | 확인한 내용 |
|---|---|---|---|---|
| BR-01 | AGG:130, REP:89 | 예 | 예 | 두 줄 모두 `sub.status <> 'X'`. 비고의 SCH:79(CHECK 없음)·SCH:65(주석) 일치 |
| BR-02 | AGG:123-126, :132 / REP:81-84, :93 | 예 | 예 | `ROW_NUMBER … PARTITION BY student_id, assignment_id ORDER BY submitted_at DESC, id DESC`, `WHERE v.rn = 1`(AGG:132, REP:93) |
| BR-03 | AGG:104-109, REP:134 | 예 | 예 | `FROM dbo.unit LEFT JOIN assignment`, 학급 조건 없음. SEED:18-20·24-28 의 학급 · 단원 일치 |
| BR-04 | AGG:107-108, :177-184 | 예 | 예 | LEFT JOIN, `v.assignment_id = @assignment_id`(:182), `@found=0` 분기(:184). 비고 `:90` PK 일치, SCH:52 주석 일치 |
| BR-05 | AGG:202-203, REP:71-72 | 예 | 예 | `IF @raw IS NULL SET @raw = 0.0`, `ISNULL(v.score, 0.0)`. 주석 :114 일치 |
| BR-06 | AGG:208-214, REP:69-74 | 예 | 예 | `> DATEADD(DAY, 2, @due_at)`, `ROUND(@raw * 0.9, 1)`. SCH:60 `due_at NOT NULL` 일치 |
| BR-07 | AGG:184-197 | 예 | 예 | `@is_missing = 1`, `@adj = 0.0`. 비고 :105 · :161 의 `unit.weight` 일치 |
| BR-08 | AGG:189-194 | 예 | 예 | `IF @unit_weight = 0.00 … FETCH NEXT … CONTINUE`. 변경 이력 :17 일치 |
| BR-09 | AGG:236-243 | 예 | 예 | `>= 90.0/80.0/70.0/60.0`, `ELSE 'F'` |
| BR-10 | AGG:281-290, :308 | 예 | 예 | `CASE @unit_code … 0.30/0.25/0.25/0.20/0.00`, `@total + (@unit_score * @w)`, `ROUND(@total, 1)`. TODO :280, 이력 :16, DECIMAL :86 · :249 · :250, SCH:41 모두 일치 |
| BR-11 | AGG:311-317 | 예 | 예 | `ROUND(@total, 1) >= 90.0 …`. 중복 구간 :238-241 · :312-315 일치 |
| BR-12 | AGG:292-295, :309-310 | 예 | 예 | `has_late`/`has_missing` 설정, TOTAL VALUES 에 전달. 결과 SELECT(:420-424)에 플래그 없음 |
| BR-13 | AGG:428-430 | 예 | 예 | `ORDER BY c.student_id, CASE WHEN … 'TOTAL' …, c.unit_code`. `student_id VARCHAR(20)`(:59) |
| BR-14 | AGG:43-52 | 예 | 예 | `IF @class_name IS NULL … WHERE 1 = 0; RETURN`. REP:37 재적 0 판정 일치. DELETE 로 학급 행 전부 삭제된다는 추론은 :359-367 과 일치(미실행) |
| BR-15 | AGG:333-344, :347-356, :359-367 | 예 | 예 | UPDATE · INSERT NOT EXISTS · DELETE NOT EXISTS. PK 에 class_id 없음 SCH:105, `gs.class_id = @class_id`(:334) 일치 |
| BR-16 | AGG:390-391, :401-405 | 예 | 예 | `@saved IS NULL OR @saved <> @adj`, `ROLLBACK`, `RAISERROR`. 주석 :369, `@row_count` :34 · :393 일치(비교에 안 쓰임 확인) |
| BR-17 | AGG:409-413 | 예 | 예 | `UPDATE sub SET aggregated_at = @now … JOIN #stu`, status 조건 없음. `SYSDATETIME()` :30 |
| BR-18 | REP:125-126 | 예 | 예 | `ROUND(ISNULL(e.sum_score, 0.0) / @enrolled, 2)`. 주석 :18 일치 |
| BR-19 | REP:122-124 | 예 | 예 | `u.weight = 0.00 … submitted = 0 THEN NULL ELSE ROUND(sum/submitted, 2)` |
| BR-20 | REP:128-133 | 예 | 예 | `e.max_score`(:128), `min` CASE(:129-133). `:135-145` LEFT JOIN 일치 |
| BR-21 | REP:116-118, :136-143 | 예 | 예 | `ISNULL(e.submitted,0)`, `@enrolled - …`, `ISNULL(e.late,0)`. 비고 REP:77 · :143, AGG:182 · :218 일치 |
| BR-22 | REP:146-155 | 예 | 예 | `COUNT(*) AS excluded … sub.status = 'X' GROUP BY unit_code`, ROW_NUMBER 없음 |
| BR-23 | REP:164-175 | 예 | 예 | `reported_at = @now`(:165), `last_report_at = @now`(:173). 잠금 순서 REP:158-159, AGG:327-328 일치 |
| BR-24 | GC:23, :83-88 | 예 | 예 | `DEFAULT_CLASS = "C1"`, `null || isBlank → DEFAULT_CLASS`, `trim()` |
| BR-25 | GR:45-53 | 예 | 예 | null → `""`, `BigDecimal → toPlainString()`, 그 외 `toString().trim()` |
| BR-26 | GC:57-58, :108-113 | 예 | 예 | `catch (DataAccessException)`, `getMostSpecificCause()`, `BAD_GATEWAY` |
| BR-27 | AGG:85-86, REP:107-109 | 예 | 예 | `raw/adj DECIMAL(5,1)`, `avg DECIMAL(5,2)`, `max/min DECIMAL(5,1)`. SCH:77 일치 |
| BR-28 | REP:24, AGG:24, GC:83-88 | 예 | 예 | 양쪽 `@class_id VARCHAR(10)`, GC 는 trim 만 함. GC:68 `esc(cid)` 제목 출력 일치. 테스트 근거 서술은 이슈 #5 |

### BR 밖의 인용(같은 문서 안)

| 위치 | 인용 근거 | 존재 | 뒷받침 | 비고 |
|---|---|---|---|---|
| 매직 넘버 표(350-364) 10행 | AGG · REP · GC 줄번호 전부 | 예 | 예 | `'X'` REP:152, 2일 AGG:209 · REP:70 · :74, 0.9 AGG:213 · REP:71, 가중치 AGG:282-287 등 일치 |
| 요약 표(366-374) 5행 | AGG:105 · :281-288 · :182 · :218 · :43 · :213 · :308 · :327-328, REP:115 · :122 · :77 · :143 · :37 · :124 · :126 · :158-159 | 예 | 예 | 일치 |
| 주석만 있는 것(376-381) | SCH:41 · :52 · :65, AGG:16 | 예 | 예 | 일치 |
| 검증 이력(383-400) | AGG · REP · SCH 줄번호 | 예 | 예 | AGG:236-243, :369, :390-391, REP:62 · :137 등 일치. 단 `CX-nn` 미정의(#2) |
| 이관 대조 BR-24 · BR-28 · 빈 학급 | SVC:81-84, :63 | 예 | 예 | `toProcedureArgument`(:82-85), `countEnrolled`/`enrolled == 0`(:63-64) |
| 이관 대조 BR-01·02 / 05·06 / 21 | SVC:90-111, :102, :115-118, :103, :161, :179 | 예 | 일부 | :90-111 · :115-118 · :161 일치. :179 · :103 은 한 줄 어긋남(#6) |
| 이관 대조 BR-22 · 18·19 · 20 · 25·27 | SVC:121-128, :138-144, :170-173, :147-155, :188-190 | 예 | 예 | 일치 |
| 이관 대조 단원 순서 | REPO:28-29 | 예 | **아니오** | 이슈 #1. 실제는 `GradeReportRepository.java:38-40` |
| 이관 대조 BR-26 · CORS · 연결 풀 | `GlobalExceptionHandler` `handleDatabaseUnavailable`, WebConfig:18-20, application.properties:11-13 | 예 | 예 | `handleDatabaseUnavailable`(GlobalExceptionHandler.java:48), WebConfig:18-20 `/api/**` · `localhost:5173` · GET, 풀 최대 3(:12) · 5000ms(:13)(:11 은 initialization-fail 줄) · `application.yml` grades 풀 2 · 3000ms 일치 |
| 이관 회고 테스트 이름 9개 | `averageTruncatesIntermediateLikeTsql` 외 8개 | 예 | 예 | `modern/api/src/test` 에서 `void <이름>` 각 1건 확인 |
| 시드 C4 | SEED 끝, 제출 901~910 | 예 | 예 | SEED:224-251 일치 |

## 확인 필요

| 파일 또는 범위 | 확인할 질문 |
|---|---|
| `legacy/` 미변경 · 요청 1건 = 커밋 범위 1개 | 이번 세션은 git 조작이 금지돼 `git diff --stat` 을 보지 못했다. 사람이 문서 외 파일(특히 `legacy/`)이 변경 범위에 없는지 확인해야 한다 |
| 이슈 표의 `파일:줄번호` | 체크리스트 0장에 따라 사람이 원본과 한 번 더 대조하고 어긋난 행에 "(사람 판정)"을 표시해야 한다 |
| #7 범위 | 이관 대조 · 회고 구간이 이번 요청에 포함되는지 사람이 결정한다 |

## 코드용 기준을 문서에 쓸 때

- **그대로 쓰인 것**: [반려] / [경고] 구분과 "[반려] 1건 = 반려", 이슈 4항목(심각도 · 파일:줄번호 · 근거 · 수정 방향), "줄번호를 못 대면 확인 필요", 리뷰 세션은 수정하지 않기, 판정 형식(판정 줄 → 기준표 → 이슈 → 확인 필요), `legacy/` 읽기 전용.
- **바꿔야 했던 것**: "테스트 통과"는 "인용 근거 전수 대조"가 된다. 실행 결과(통과 · 실패 수)가 없으므로 합격 증거가 "예/아니오 + 확인한 줄 내용"이 되어 규칙별 표가 필수다. "치명 이슈 0건"은 "근거 없는 규칙 · 허위 인용 0건", "컨벤션 준수"는 "약칭 정의 · 예/아니오 문장"으로 바뀌고, 범위 이탈은 변경 파일이 아니라 문서 구간(이관 대조 · 회고)이 요청 문장에 드는지로 본다.
- **안 맞는 것**: `./gradlew test` · `npm test` · `characterization` 실행, `@Disabled` · `.skip`, SQL 문자열 이어 붙이기, 인증 건너뛰기, 컨트롤러 · 서비스 · web 컨벤션, `build.gradle` · `package.json` 변경 승인은 문서 리뷰에 해당이 없다(문서 새 파일이라 diff 로 기존 줄 변경도 볼 수 없다).
- **경계에 걸린 것**: "근거 줄번호 사람 대조"는 AI 가 전수 대조해도 사람 대조를 대체하지 못하므로 확인 필요에 남겼다. 문서 하나가 레거시 규칙 · 이관 대조 · 회고를 모두 담으면 인용 오류가 범위 밖 구간에서 나와 판정이 갈리므로, 문서를 용도별로 나누는 편이 판정이 선명하다.
