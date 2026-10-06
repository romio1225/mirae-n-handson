# RCA: d-mssql-deadlock — MS-SQL 데드락으로 성적 집계 지연

- 대상: `incident-logs/d-mssql-deadlock/` (`app-timeout.log`, `mssql-wait.log`)
- 작성: 2회차 Day 2-2 실습 1 (2026-10-06). 로그는 더미이며, 이 문서의 발췌의 민감정보는 맨 아래 마스킹 규칙으로 가렸다.
- 읽은 방법: 파일을 통째로 읽지 않고 `wc` · `head` · `tail` · `grep -c` · `grep -n` · `awk` 집계 · `sed -n` 구간만 봤다.

## 요약

- **무엇이**: 성적 서버(:8083)의 C2 학급 성적 집계 · 리포트 호출이 느려지고(2~9초) 15초 타임아웃 25건, 502(데드락) 7건이 났다. C1 · C3 은 정상.
- **언제**: 2026-09-18 15:48:12 ~ 16:26:12 KST(약 38분). 첫 신호는 DB 잠금 대기(15:48), 첫 오류는 10분 뒤(15:58).
- **원인**: `usp_aggregate_grades` 와 `usp_class_report` 가 같은 학급의 `grade_summary` · `submission` 을 서로 반대 순서로 잠근다. 같은 학급에 두 호출이 겹치면 잠금 대기 → 데드락.
- **확신**: 메커니즘은 높음(로그 대기 위치 126건이 두 UPDATE 줄에 모이고 코드가 일치). 15:48 에 시작한 계기는 낮음(로그 없음).
- **조치 방향**: 두 프로시저의 잠금 순서 통일(6절), 잠금 대기 · 데드락 경보 추가(8절).

## 1. 수집 범위

### 1.1 파일별 기간 · 형식

| 파일 | 줄 수 | 첫 시각 | 마지막 시각 | 시각 형식 | 타임존 |
|---|---|---|---|---|---|
| `app-timeout.log` | 261 | 2026-09-18 15:30:23.608 | 2026-09-18 16:40:38.270 | `YYYY-MM-DD HH:MM:SS.mmm +0900` | `+0900`(KST) 명시 |
| `mssql-wait.log` | 304 | 2026-09-18 15:30:00.03 | 2026-09-18 16:39:00.03 | `YYYY-MM-DD HH:MM:SS.cc` (1/100초) | **표기 없음 → KST 로 판단** |

**KST 변환 규칙: 두 파일 모두 KST 로 그대로 쓴다(변환 없음).** 근거: 같은 데드락이 `mssql-wait.log:71` 에 `16:02:41.54`, `app-timeout.log:121`(502)에 `16:02:41.590` 으로 0.05초 차이로 찍힌다. 나머지 6건도 0.05~0.08초 간격으로 짝이 맞는다(`mssql-wait.log:102·134·169·201·240·280` ↔ `app-timeout.log:128·144·156·166·190·211`). UTC 였다면 9시간이 어긋난다. 두 파일은 다른 서버 시계라 1초 이내의 선후는 단정하지 않는다.

### 1.2 이상 건수 (분 단위)

| 지표 | 평소(15:30–15:47, 16:27–16:40) | 이상 구간 | 근거 |
|---|---|---|---|
| `mssql-wait.log` `lock_wait`(LCK_M_U) | 0건/분 | 15:48 부터 1~5건/분, 데드락이 난 분은 24~26줄/분(데드락 그래프 포함). 총 126건 | 첫 줄 `mssql-wait.log:19`(15:48:12.40), 끝 줄 `:291`(16:25:44.65) |
| `mssql-wait.log` `sampler ok` (1분 간격) | 매분 1줄, `blocked_sessions=0` | **15:48–16:26 동안 0줄**(기록 공백) | 공백 전 `:18`(15:47:00.03), 재개 `:292`(16:27:00.03) |
| `mssql-wait.log` 데드락(Error 1205) | 0 | 7건(16:02, 16:05, 16:07, 16:11, 16:14, 16:18, 16:23) | `:71` … `:280` |
| `app-timeout.log` 200 OK 평균 응답시간(3분 묶음) | 226~289ms | 15:48 묶음 1,066ms → 15:54~16:24 묶음 2,100~4,689ms | 첫 느린 호출 `app-timeout.log:63`(15:48:51.603, 2,837ms) |
| `app-timeout.log` WARN(`slow_call=true`) | 0 | 52건, 15:55~16:25 | 첫 줄 `:84`, 끝 줄 `:217` |
| `app-timeout.log` ERROR timeout(15,000ms) | 0 | 25건, 15:58~16:22 | 첫 줄 `:101`, 끝 줄 `:208` |
| `app-timeout.log` ERROR 502(deadlocked) | 0 | 7건, 16:02~16:23 | 첫 줄 `:121`, 끝 줄 `:211` |
| WARN · ERROR 의 학급 | — | **84건 전부 `class_id=C2`** | `grep -E "WARN|ERROR" app-timeout.log | grep -o "class_id=C[0-9]"` 집계 |

- **장애 구간 제안: 2026-09-18 15:48:12 ~ 16:26:12 KST** — 시작은 첫 lock_wait(`mssql-wait.log:19`, 앱 첫 지연 `app-timeout.log:63` 보다 39초 앞), 끝은 마지막 lock_wait(`:291`) · WARN(`app-timeout.log:217`) 뒤 정상 복귀(`app-timeout.log:218`, sampler 재개 `mssql-wait.log:292`).

### 1.3 앞뒤 여유

- 앞쪽 18분(15:30, 로그 시작 — 더 늘릴 수 없음), 뒤쪽 5분(16:31, 정상 복귀 확인). **분석 대상: 15:30:00 ~ 16:31:00 KST**.

## 2. 타임라인

형식: 시각(KST) | 출처(파일:줄번호) | 사건 요약 | 원문 발췌(80자 이내, 민감값 가림). `app` = `app-timeout.log`, `mssql` = `mssql-wait.log`.

| 시각 | 출처 | 사건 요약 | 원문 발췌 |
|---|---|---|---|
| 15:30:00.03 | mssql:1 | 평소: sampler 매분 정상, 이후 15:47 까지 같은 줄 매분 1건 | `monitor sampler ok blocked_sessions=0 open_tran=0` |
| 15:30:23.608 | app:1 | 평소: aggregate · report 200 OK 140~400ms, 15:47 까지 C1 · C2 · C3 섞여 호출 | `INFO [grade-client] GET http://10.20.x.x:8083/aggregate?class_id=C2 -> 200 OK in 240ms` |
| 15:47:00.03 | mssql:18 | 마지막 sampler 줄. 이후 16:27:00 까지 sampler 기록 없음 | `monitor sampler ok blocked_sessions=0 open_tran=0` |
| 15:48:12.40 | mssql:19 | **첫 lock_wait**. `usp_class_report` line 172 가 `grade_summary` 키 U 잠금 대기, 막은 쪽은 `usp_aggregate_grades` | `lock_wait session_id=74 blocked_by=66 wait_type=LCK_M_U wait_ms=2227 object=grades.dbo.grade_summary` |
| 15:48:51.603 | app:63 | 첫 느린 응답(INFO, 경고 아님). C2 aggregate 2,837ms | `GET …/aggregate?class_id=C2 -> 200 OK in 2837ms` |
| 15:48:58.08 | mssql:20 | 반대 방향 lock_wait. `usp_aggregate_grades` line 409 가 `submission` 키 대기, 막은 쪽은 `usp_class_report` | `lock_wait session_id=58 blocked_by=71 wait_type=LCK_M_U wait_ms=2295 object=grades.dbo.submission` |
| 15:49 ~ 15:55 | mssql:21–30 | lock_wait 반복, 이후 1~5건/분(16:25 까지 총 126건). 대기 위치는 항상 `usp_class_report` line 172(69건) 또는 `usp_aggregate_grades` line 409(57건) | `lock_wait … wait_ms=1502~4303 …` |
| 15:50 ~ 15:54 | app:67, 69, 70, 73, 82 | C2 호출만 1,345~2,837ms, 같은 시각 C1 · C3 는 120~360ms | `GET …/report?class_id=C2 -> 200 OK in 2283ms` (app:70) |
| 15:55:20.453 | app:84 | **첫 WARN**(slow_call) | `WARN … /aggregate?class_id=C2 -> 200 OK in 3989ms … slow_call=true` |
| 15:58:51.327 | app:101 | **첫 ERROR**: report 15초 타임아웃. 이후 timeout 25건(16:22:19, app:208 까지) | `ERROR … /report?class_id=C2 -> timeout after 15000ms (java.net.SocketTimeoutException` |
| 16:02:41.53 | mssql:51–70 | **첫 데드락 그래프**. 희생자 process2b950298. 자원 2개가 엇갈림: `grade_summary` PK(X 보유 ↔ U 대기) · `submission` PK(X 보유 ↔ U 대기). 한쪽 `usp_aggregate_grades` line 409(mssql:56), 다른 쪽 `usp_class_report` line 172 `last_report_at` 갱신(mssql:61–62). 둘 다 C2 | `deadlock victim=process2b950298` (mssql:52) |
| 16:02:41.54 | mssql:71 | Error 1205(희생 트랜잭션 롤백) | `spid58 Error: 1205, Severity: 13, State: 51. Transaction (Process ID 58) was deadlocked` |
| 16:02:41.590 | app:121 | 앱이 502 수신 (선후 불확실 — mssql:71 과 0.05초 차, 다른 서버 시계) | `ERROR … /report?class_id=C2 -> 502 Bad Gateway … msg="Transaction (Process ID …) was deadlocked` |
| 16:05:12 ~ 16:23:15 | mssql:82–102, 114–134, 149–169, 181–201, 220–240, 260–280 / app:128, 144, 156, 166, 190, 211 | 데드락 6건 더(총 7건, 2.5~4.7분 간격). 모두 같은 두 프로시저 · 같은 두 줄 · C2. 각 건마다 앱 502 1건이 0.05~0.08초 뒤에 찍힘(선후 불확실) | `Error: 1205 …` / `502 Bad Gateway …` |
| 16:23:15.93 | mssql:280 / app:211 | 마지막 데드락 · 마지막 502 | `spid66 Error: 1205 …` |
| 16:25:44.65 | mssql:291 | 마지막 lock_wait | `lock_wait session_id=66 blocked_by=74 … wait_ms=5362` |
| 16:25:50.720 | app:217 | 마지막 WARN | `WARN … /aggregate?class_id=C2 -> 200 OK in 4771ms … slow_call=true` |
| 16:26:01.911 | app:218 | 정상 응답 복귀(이후 200~400ms) | `INFO … /aggregate?class_id=C3 -> 200 OK in …ms` |
| 16:27:00.03 | mssql:292 | sampler 기록 재개, `blocked_sessions=0` | `monitor sampler ok blocked_sessions=0 open_tran=0` |
| 16:30 (결과) | app 집계 | 장애 구간 WARN · ERROR 84건이 모두 C2. 구간 중 호출 수는 C2 aggregate 77 · report 24, C1 29, C3 30(구간 전 18분: C2 26, C1 19, C3 16) | `awk` 집계 |

가장 시끄러운 오류보다 **앞선 조용한 줄**: 첫 lock_wait(`mssql:19`, 첫 ERROR 10분 39초 전), sampler 기록 공백(`mssql:18` → `:292`), 경고 없이 느려진 INFO 응답(`app:63`).

로그로 확인할 수 없는 것: 15:48 직전에 무엇이 바뀌었는지(배포 · 설정 · 배치 시작)는 두 파일 어디에도 기록이 없다. 원인 해석은 3절(가설)에서 다룬다.

## 3. 가설과 검증

가장 많이 나온 오류(앱 `timeout after 15000ms` 25건, `502 … deadlocked` 7건, `Error: 1205` 7건)는 **증상**으로 두고, 그보다 앞선 15:48 의 lock_wait 부터 설명하는 가설을 계층별로 세운다.

### 3.1 가설 · 반증 조건 · 로그 검증

| # | 계층 | 가설 | 지지 근거(타임라인) | 반증 조건 — 이것이 보이면 틀린 것 | 확인 방법 | 실행 결과(숫자) | 로그 판정 |
|---|---|---|---|---|---|---|---|
| H1 | DB · 쿼리 | 같은 학급(C2)에 `usp_aggregate_grades` 와 `usp_class_report` 가 겹쳐 돌 때, 두 프로시저가 `grade_summary` · `submission` 을 **서로 반대 순서로** 잠가 대기 · 데드락이 난다 | `mssql:19` · `mssql:20`(서로 상대를 막는 lock_wait), 데드락 그래프 `mssql:51-70`(두 키 자원이 X 보유 ↔ U 대기로 엇갈림) | ① lock_wait 의 대기 위치가 두 프로시저의 특정 두 줄로 모이지 않고 흩어져 있다 ② 데드락 그래프의 두 자원이 같은 테이블이다 ③ 데드락 상대 중 한쪽이 두 프로시저가 아니다 | `grep -o "proc=… line=[0-9]*" mssql-wait.log \| sort \| uniq -c`, `grep "frame procname" … \| uniq -c`, `grep inputbuf …` | lock_wait 126건 = `usp_class_report` line 172 **69건** + `usp_aggregate_grades` line 409 **57건**(다른 위치 0). 데드락 7건 모두 frame 이 같은 두 줄(각 7건), 자원은 `grade_summary` PK 와 `submission` PK(`mssql:58`·`:62` 외), inputbuf 14건 모두 `@class_id = 'C2'` | **유지** — 반증 조건 ①②③ 모두 해당 없음 |
| H2 | 트래픽 · 인프라 | 15:48 직전 C2 요청이 급증해 동시 실행이 늘었다 | 장애 구간 C2 호출 101건(분당 2.6건) > 장애 전 26건(분당 1.4건) | 장애가 **시작된** 15:48~15:54 의 C2 분당 요청 수가 장애 전 평소(15:30~15:47)보다 많지 않다 | `awk '$2>="15:36" && $2<"15:58" && /class_id=C2/ {print substr($2,1,5)}' app-timeout.log \| sort \| uniq -c` | 장애 전 15:30~15:47: C2 **26건/18분 = 1.44건/분**. 시작 구간 15:48~15:54: **7건/7분 = 1.0건/분**(15:48 1, 15:50 2, 15:51 1, 15:52 1, 15:54 1). 증가는 15:55 이후(15:57 4건/분) — 첫 지연(`app:63`, 15:48:51)보다 뒤 | **기각** — 시작 시점의 요청은 오히려 적다. 늘어난 호출은 결과(H3) |
| H3 | 애플리케이션 · 사용자 | 느려진 화면을 사용자 · 클라이언트가 반복 호출(재시도 · 새로고침)해 C2 동시 실행이 늘고 대기 · 데드락이 길어졌다(악화 요인) | 구간 중 교사A가 C2 report 21건(전체 24건 중), 학생A가 C2 aggregate 35건 | 반복 호출 주체가 장애 **전**에도 같은 빈도로 호출했다, 또는 반복 호출이 첫 lock_wait(15:48:12)보다 먼저 시작됐다 | 상위 호출자별 구간 전/중/후 건수와 첫 호출 시각(`awk`, `grep -n`) | 교사A: 전 **4** / 중 **27** / 후 **4**건. 학생A: 전 **2** / 중 **35** / 후 **1**건, 구간 중 첫 호출 15:57:15(`app:93`, 첫 lock_wait 9분 뒤), 호출 간격 11~83초 | **유지(악화 요인으로만)** — 시작보다 뒤에 늘었으므로 계기는 아니다 |
| H4 | 배포 · 설정 · 데이터 변경 | 15:48 직전에 무언가 바뀌어(프로시저 · 인덱스 배포, C2 데이터 증가, 배치 시작) 두 프로시저의 실행 시간이 길어졌다 | 15:47 까지 C2 응답 200~400ms(`app:54-61`) → 15:48:51 2,837ms(`app:63`) | 15:48 전후로 C2 단독 호출(동시 실행 없음)의 응답 시간이 같다, 또는 변경 기록이 없다 | 배포 · 변경 이력, 프로시저 실행 시간(DMV), C2 행 수 추이 | 두 파일에 배포 · 설정 · 배치 기록 **0건**(`grep -c -i -E "deploy\|config\|batch\|restart" *.log` = 0). sampler 도 15:48~16:26 기록 공백(`mssql:18` → `:292`) | **판단 불가(로그 부족)** — 배포 이력, 프로시저별 실행 시간(`sys.dm_exec_procedure_stats`), 테이블 행 수 추이, 그리고 장애 중 끊긴 sampler 기록이 있었다면 판단할 수 있었다 |

### 3.2 코드 · 설정 대조

대조한 곳: `legacy/grade-mssql/sql/usp_aggregate_grades.sql`, `legacy/grade-mssql/sql/usp_class_report.sql`, `db/mssql/init/01-schema.sql`, `legacy/grade-mssql/src/main/java/com/example/grade/GradeRepository.java` (읽기만 함)

| 가설 | 판정 | 근거(파일:줄번호) |
|---|---|---|
| H1 | **코드가 지지한다** | `usp_aggregate_grades.sql:330` 트랜잭션 시작 → `:333-344` `grade_summary` UPDATE(X 잠금) → `:347-366` INSERT · DELETE → `:373-399` 커서로 한 건씩 검증(잠금을 쥔 채) → **`:409-413` `submission` UPDATE** → `:415` COMMIT. 주석 `:327` "잠금 순서: grade_summary → submission". 반대쪽 `usp_class_report.sql:161` 트랜잭션 시작 → **`:164-169` `submission` UPDATE**(`reported_at`) → **`:172-175` `grade_summary` UPDATE**(`last_report_at`) → `:177` COMMIT. 주석 `:158-159` "잠금 순서: submission → grade_summary (usp_aggregate_grades 는 grade_summary → submission 순서)". 로그의 대기 위치 line 409 · line 172 가 이 두 UPDATE 와 정확히 같다 |
| H1 보강 | 지지 | `usp_aggregate_grades.sql:369-399` 의 커서 검증이 `grade_summary` X 잠금을 쥔 채 행 단위로 돌아 잠금 보유 시간을 늘린다(충돌 창이 넓어짐). `db/mssql/init/01-schema.sql:12` `READ_COMMITTED_SNAPSHOT OFF` — 다만 데드락 자원은 두 UPDATE 의 U/X 잠금이라 스냅샷 격리로는 풀리지 않는다 |
| H3 | 관련 코드를 찾지 못했다 | 레거시 호출부 `GradeRepository.java:25`, `:30` 은 `EXEC` 한 번씩 부르고 재시도 코드는 없다. 반복 호출은 클라이언트(교사 · 학생 화면) 쪽으로 보이나 그 코드는 이 저장소에 없다 |
| H4 | 관련 코드를 찾지 못했다 | 15:48 의 변화를 가리키는 코드 · 설정 · 이력이 저장소와 로그에 없다 |

### 판정

- **채택: H1 — 잠금 순서 역전.** `usp_aggregate_grades` 는 `grade_summary → submission`(`usp_aggregate_grades.sql:333`, `:409`), `usp_class_report` 는 `submission → grade_summary`(`usp_class_report.sql:164`, `:172`) 순서로 같은 학급의 행을 한 트랜잭션 안에서 갱신한다. 같은 학급(C2)에 두 프로시저가 겹치면 서로 상대가 쥔 키를 기다린다(`mssql-wait.log:19-20`), 둘 다 놓지 않으면 데드락으로 한쪽이 희생된다(`mssql-wait.log:51-71`, 7건). 앱의 15초 타임아웃(`app-timeout.log:101` 외 24건)과 502(`app-timeout.log:121` 외 6건)는 그 결과다.
  - **확신: 높음(메커니즘)** — 로그의 대기 위치 126건이 모두 두 UPDATE 줄로 모이고, 코드 주석이 반대 순서를 스스로 적고 있다.
  - **확신: 낮음(왜 15:48 에 시작했나)** — 두 프로시저는 장애 전에도 C2 에 대해 같이 불렸다(장애 전 C2 aggregate 22 · report 4건). 15:48 부터 겹침이 잦아진 계기(H4)는 로그로 확인할 수 없다.
- **기각: H2 트래픽 급증** — 장애 시작 구간(15:48~15:54) C2 요청이 1.0건/분으로 평소 1.44건/분보다 적다. 요청 증가는 15:55 이후의 결과다.
- **악화 요인으로만 유지: H3 재호출** — 교사A(전 4 → 중 27건), 학생A(전 2 → 중 35건)의 반복 호출이 C2 동시 실행을 늘렸다. 시작(15:48:12)보다 뒤(학생A 첫 호출 15:57:15)라 원인은 아니다.
- **판단 불가: H4 계기** — 배포 이력 · 프로시저 실행 시간 · 행 수 추이가 없다. 수집 대상은 5절(모니터링)에서 다룬다.
- 참고: 장애 학급이 C2 하나인 것은 H1 과 맞는다. 데드락은 **같은 학급**의 행을 두 프로시저가 동시에 잡을 때만 나고(두 UPDATE 모두 `class_id` · 학급 학생으로 범위를 좁힌다, `usp_class_report.sql:169`, `:175`), C2 가 가장 자주 호출되는 학급이다(장애 전 26건 vs C1 · C3 각 19 · 16건).

## 4. 원인

- **직접 원인 — 잠금 순서 역전으로 인한 대기와 데드락**
  1. `usp_aggregate_grades`(C2) 가 트랜잭션 안에서 `grade_summary` 의 C2 행을 먼저 갱신해 X 잠금을 쥔다.
  2. 같은 시각 `usp_class_report`(C2) 가 자기 트랜잭션에서 `submission` 의 C2 학생 행을 먼저 갱신해 X 잠금을 쥔다.
  3. 집계는 이어서 `submission` 을, 리포트는 `grade_summary` 를 갱신하려다 서로 상대의 잠금을 기다린다(LCK_M_U).
  4. 대기가 짧게 끝나면 응답만 느려지고(2~9초), 순환이 완성되면 SQL Server 가 한쪽을 희생시켜 Error 1205 → 앱 502 가 된다. 대기가 15초를 넘으면 앱 타임아웃이 된다.
- **배경 원인 — 대기를 가능하게 하고 길게 만든 조건**
  - 조회 화면인 리포트가 매번 쓰기(`reported_at`, `last_report_at` 기록, BR-23)를 한다. 읽기만 했다면 이 잠금 충돌은 없다.
  - 집계 트랜잭션 안에서 커서로 한 건씩 검증해 `grade_summary` 잠금 보유 시간이 길다.
  - 두 프로시저의 갱신 순서를 맞추는 규칙 · 검사가 없다(주석으로만 반대 순서가 적혀 있다).
- **악화 요인**: 느려진 화면을 교사A · 학생A 등이 반복 호출해 C2 동시 실행이 늘었다(15:55 이후).

## 5. 근거 로그 줄

| 원인 서술(4절) | 근거 | 발췌 |
|---|---|---|
| 직접 1 · 3: 리포트가 `grade_summary` 에서 대기, 막은 쪽은 집계 | `mssql-wait.log:19` | `lock_wait … LCK_M_U … object=grades.dbo.grade_summary … proc=…usp_class_report line=172 blocker_proc=…usp_aggregate_grades` |
| 직접 2 · 3: 집계가 `submission` 에서 대기, 막은 쪽은 리포트 | `mssql-wait.log:20` | `lock_wait … object=grades.dbo.submission … proc=…usp_aggregate_grades line=409 blocker_proc=…usp_class_report` |
| 직접 3: 대기 위치가 두 줄뿐 | `mssql-wait.log:19-291` 집계 | line=172 69건, line=409 57건, 그 밖 0건 |
| 직접 4: 순환 완성 → 데드락, 자원 2개 엇갈림 | `mssql-wait.log:51-52`, `:58`, `:62` | `deadlock victim=process2b950298` / `keylock … grade_summary … mode=X` / `keylock … submission … mode=X` |
| 직접 4: 희생 트랜잭션 롤백 | `mssql-wait.log:71` | `Error: 1205, Severity: 13 … was deadlocked on lock resources` |
| 직접 4: 앱 502 | `app-timeout.log:121` | `/report?class_id=C2 -> 502 Bad Gateway … msg="Transaction (Process ID …) was deadlocked` |
| 직접 4: 앱 타임아웃 | `app-timeout.log:101` | `/report?class_id=C2 -> timeout after 15000ms (java.net.SocketTimeoutException` |
| 직접 4: 대기만 하면 느려짐 | `app-timeout.log:63` | `/aggregate?class_id=C2 -> 200 OK in 2837ms` |
| 배경: 리포트가 쓰기를 한다 | `mssql-wait.log:62` | `UPDATE gs SET gs.last_report_at = @now FROM dbo.grade_summary AS gs WHERE gs.class_id` |
| 악화: 반복 호출은 시작 뒤 | `app-timeout.log:93` | 학생A 의 구간 중 첫 호출 15:57:15 (`WARN … /aggregate?class_id=C2 … slow_call=true`) |

코드 근거는 3.2 절(`usp_aggregate_grades.sql:333`, `:409` / `usp_class_report.sql:164`, `:172`).

## 6. 수정안 (지금 적용할 것, 제안만 — 코드는 고치지 않음)

1. **`legacy/grade-mssql/sql/usp_class_report.sql:163-175` 의 두 UPDATE 순서를 바꾼다**: 3-2(`grade_summary.last_report_at`)를 먼저, 3-1(`submission.reported_at`)을 뒤에. 그러면 두 프로시저 모두 `grade_summary → submission` 순서가 되어 순환이 생기지 않는다. 주석 `:158-159` 도 같이 고친다. (`legacy/` 수정이므로 사람 승인 후 적용)
2. **`legacy/grade-mssql/sql/usp_aggregate_grades.sql:369-399` 의 커서 검증을 `COMMIT`(`:415`) 뒤 또는 트랜잭션 전 집합 비교(`COUNT` · `EXCEPT`)로 옮긴다**: 잠금 보유 시간을 줄인다.
3. 이관 중인 현행 `/api/grades/report`(`modern/api/.../grade/GradeReportService.java:26`)는 BR-23 쓰기를 하지 않으므로 이 충돌이 없다. 레거시 `/report` 호출을 현행 API 로 돌리는 것도 대안이다(동작 보존 테스트 통과 상태).

## 7. 재발 방지 (같은 유형을 구조적으로 막는 장치)

- **잠금 순서 규칙**: 여러 테이블을 한 트랜잭션에서 갱신할 때의 순서를 팀 규칙으로 고정(`grade_summary → submission` 등)하고, 검증루프 체크리스트(Day 2-1)에 "새 · 수정 프로시저가 정해진 갱신 순서를 따른다"를 [반려] 항목으로 추가한다.
- **조회와 쓰기 분리**: 조회 API · 프로시저 안에서 감사용 시각 기록(BR-23)을 하지 않는다. 필요하면 별도 비동기 기록으로 뺀다.
- **같은 학급 직렬화**: 집계 · 리포트의 쓰기 구간을 `sp_getapplock @Resource = 'grade:' + @class_id` 로 학급 단위 직렬화한다.
- **호출부 방어**: 앱 클라이언트에서 Error 1205 는 짧은 지연 후 1회만 재시도하고, 사용자 새로고침은 진행 중 요청이 끝날 때까지 막는다(반복 호출 증폭 방지).

## 8. 모니터링 항목

| 지표 | 임계값(제안) | 이번 타임라인에서 울렸을 시각 | 첫 ERROR(15:58:51) 대비 |
|---|---|---|---|
| DB lock_wait 건수(LCK_M_*) | 1분에 1건 이상 + wait_ms ≥ 2,000 | **15:48:12**(`mssql-wait.log:19`, wait_ms 2,227) | **10분 39초 전** |
| DB 모니터 sampler heartbeat 누락 | 2분 이상 기록 없음 | **15:50:00**(15:48·15:49 누락, `mssql-wait.log:18` 다음 기록 16:27) | 8분 51초 전 |
| 앱 학급별 응답시간 p95 | 3분 창에서 1,000ms 초과 | **15:51 무렵**(15:48~15:50 창 평균 1,066ms, C2 2,837ms 포함) | 약 8분 전 |
| 앱 slow_call WARN(기존) | 1건 이상 | 15:55:20(`app-timeout.log:84`) | 3분 31초 전 |
| DB 데드락(Error 1205) | 1건 이상 즉시 | 16:02:41(`mssql-wait.log:71`) | 3분 50초 후 |

- 가장 이른 경보는 DB lock_wait 로, 지금 있는 경보(slow_call)보다 7분 앞선다.

## 9. 확인하지 못한 것

- **15:48 에 시작한 계기**: 배포 · 설정 · 배치 기록이 두 로그에 0건. → 배포 이력, 프로시저별 실행 시간(`sys.dm_exec_procedure_stats`), `grade_summary` · `submission` 의 C2 행 수 추이를 남긴다.
- **sampler 기록 공백(15:48~16:26)의 이유**: 모니터 자체가 막혔는지 로그로 판단 불가. → 모니터 프로세스의 자체 오류 로그를 남긴다.
- **16:02 이전 대기의 상세**: 데드락 그래프는 순환이 완성된 7건만 있다. → `blocked_process_report`(임계 5초)로 대기 중 그래프를 남긴다.
- **반복 호출의 출처**: 교사A · 학생A 의 재호출이 사람인지 화면 자동 새로고침인지 알 수 없다. → 클라이언트 요청 ID · 재시도 여부를 앱 로그에 남긴다.

---
마스킹 적용: IP 1건(`10.20.x.x`, 서버가 1대뿐이라 역할 별칭 불필요) · 이메일 0건(교사 계정은 반복 호출 구분을 위해 별칭 `교사A` 5건) · 학생 식별자 `학생A` 7건 · 토큰 0건 · 비밀번호/접속 문자열 0건. DB `session_id` 는 인증 값이 아니라 남김. 원본 로그는 고치지 않았고 대응표는 남기지 않음.
