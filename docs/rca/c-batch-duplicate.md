# RCA: c-batch-duplicate — load_datamart 중복 실행으로 데이터마트 이중 적재

## 요약

- **무엇이**: 기준일 2026-09-15 의 `dm.daily_submission_fact` 적재 건수가 369,064 건으로, 평소(7일 평균 184,617)의 정확히 2배(184,532 × 2)가 됐다. 그 위에서 `build_report_cache` 도 2배 입력으로 돌았다.
- **언제**: 2026-09-16 02:58:42 KST 수동 재실행이 락을 무시하고 시작된 시점에 이중 적재가 확정됐고(03:12:41 · 03:15:31 두 번 SUCCESS), 외부 감지는 07:00:04 `dq-check` 경고다. 약 4시간 동안 이중 데이터가 노출됐다.
- **원인**: 느리지만 진행 중이던 cron 실행(`ldm-…-0200-edf5`)을 SLA 알림만 보고 "멈췄다"고 판단한 운영자가 `force=true` 로 재실행했고, 재실행이 락을 우회(`lock=bypassed`)한 상태에서 두 실행 모두 `mode=append` 로 같은 기준일을 적재했다.
- **확신**: 메커니즘(락 우회 + append 이중 적재) **높음**, 계기(force 재실행) **높음**, 첫 실행이 왜 느렸는지 **낮음**(extract 쪽 replica lag 만 로그에 있고 load 지연 사유는 로그로 확인 불가).
- **조치 방향**: 같은 job·date 동시 실행 차단(force 가 락을 무시하지 않게), 기준일 단위 멱등 적재(append → 기준일 파티션 교체), SLA 알림에 진행률을 넣어 "느림"과 "멈춤"을 구분.

## 1. 수집 범위

| 파일 | 줄 수 | 첫 시각 | 끝 시각 | 시각 형식 · 타임존 | 읽은 방식 |
|---|---|---|---|---|---|
| `incident-logs/c-batch-duplicate/batch-job.log` | 115 | 2026-09-09 01:30:02.241 | 2026-09-16 07:00:04.610 | `YYYY-MM-DD HH:MM:SS.mmm +0900` (KST 명시) | `wc` · `head` · `tail` · `grep -c` 후 장애 구간 `sed -n 85,115p` 와 정상일 비교용 `sed -n 73,84p` |
| `incident-logs/c-batch-duplicate/datamart-counts.csv` | 25 | 2026-09-09T01:40:03+09:00 | 2026-09-16T03:33:42+09:00 | ISO 8601 `+09:00` (KST 명시) | 전체 읽음 |
| (3회차 메모용) `pipeline-samples/batch-logs/*.log` | 24~47 / 파일 | — | — | 위 로그와 같은 형식 `+0900` | `grep -c` · `grep -n` 만 |
| (3회차 메모용) `pipeline-samples/datamart-counts.csv` | 50 | 2026-09-09T00:33:46+09:00 | 2026-09-18T03:33:00+09:00 | ISO 8601 `+09:00` | 전체 읽음 |

- 두 파일 모두 타임존이 명시돼 있어(`+0900`, `+09:00`) 별도 짝짓기 없이 KST 한 축으로 맞췄다. 교차 확인: CSV 24행 `loaded_at=2026-09-16T03:15:31+09:00` ↔ `batch-job.log:110` `03:15:31.365` 두 번째 SUCCESS.
- 로그 레벨: INFO 99 · WARN 16 · **ERROR 0**.
- 분 단위 이상 건수: 2026-09-09~09-15 는 일자별 12줄로 일정하고, 2026-09-16 만 31줄. 날짜별 `status=START` 는 평소 3건, 09-16 은 4건(`load_datamart` 2건).
- **분석 대상 구간**: 2026-09-16 01:30 ~ 07:00 KST(`batch-job.log:85-115`). 비교 기준은 직전 정상일 2026-09-15(`:73-84`).

## 2. 타임라인

| 시각(KST) | 출처 | 사건 요약 | 원문 발췌(민감값 가림) |
|---|---|---|---|
| 09-09~09-15 매일 | `batch-job.log:1-84`, CSV 2-22행 | 정상: extract 601~736s, load 803~977s · 02:13~02:16 완료 · 18.2~18.6만 건 | `load_datamart … status=SUCCESS rows=186058 elapsed=923s` (:81) |
| 09-09~09-16 매일 | `batch-job.log:5,17,…,90,102` (9건) | 매 실행 validate 에서 1건 skip (평소 패턴, 이번 장애와 무관) | `step=validate skipped=1 student_id=학생A reason="class_id is NULL"` |
| 01:30:02 | `batch-job.log:85` | extract_submissions cron 시작 | `job=extract_submissions … status=START trigger=cron` |
| **01:44:07** | `batch-job.log:86` | **조용한 선행 신호** — 원천 DB replica lag 로 읽기 지연 | `note="source read slow: grade-db DB-1 replica lag 412s"` |
| 01:57:50 | `batch-job.log:87` | extract 완료, 소요 1668s(평소의 약 2.3배). 건수 41,573 은 정상 범위 | `status=SUCCESS rows=41573 elapsed=1668s` |
| 02:00:03 | `batch-job.log:88-89` | load_datamart cron 실행 `edf5` 시작, 락 획득. **mode=append** | `run_id=ldm-20260916-0200-edf5 … mode=append target=dm.daily_submission_fact` |
| **02:09:39** | `batch-job.log:91` | **조용한 지연** — 25% 도달이 평소(약 02:03:54, :78)보다 약 6분 늦음. 경고 없음 | `status=RUNNING progress=25% rows=46133` |
| 02:21~02:47 | `batch-job.log:92-93,96` | 13%p 당 약 12분씩 **계속 진행**(38% → 51% → 64%). 멈춘 것이 아님 | `progress=64% rows=118100` (:96) |
| 02:40:03 | `batch-job.log:94` | 러너 자체 경고: p95 초과 | `note="elapsed 40m exceeds p95 17m" rows=94111` |
| 02:45:00 | `batch-job.log:95` | SLA 모니터 알림. **상태는 RUNNING 으로 표시** | `[sla-monitor@MON-1] … status=NOT_FINISHED sla=02:45 last_status=RUNNING` |
| 02:58:41 | `batch-job.log:97` | 운영자A 가 "멈춘 것으로 보고" force 재실행 요청 | `rerun requested … operator=운영자A reason="SLA 알림 — 멈춘 것으로 보고 재실행" force=true` |
| 02:58:41 | `batch-job.log:98` | batchctl → 러너 API 호출 201 | `POST http://BATCH-1:8793/api/v1/jobs/load_datamart/runs … Bearer <token> -> 201` |
| **02:58:42** | `batch-job.log:99` | **결정적 사건** — 락이 edf5 에 잡혀 있음을 알고도 `--force` 로 무시 | `lock=held_by run_id=…-0200-edf5 pid=46735 action=ignored reason="--force"` |
| 02:58:42 | `batch-job.log:100-101` | 수동 실행 `8963` 시작, 락 우회, 같은 원천을 **append** | `run_id=ldm-20260916-0258-8963 status=START trigger=manual … lock=bypassed` |
| 02:58:51~03:11:18 | `batch-job.log:103-107` | 두 실행이 동시에 진행(edf5 77→90%, 8963 25→75%) | `run_id=…-0200-edf5 status=RUNNING progress=77%` (:103) |
| 03:12:41 | `batch-job.log:108-109` | cron 실행 edf5 완료 184,532건, 락 해제 | `run_id=…-0200-edf5 status=SUCCESS rows=184532 elapsed=4357s` |
| 03:15:31 | `batch-job.log:110-111` | 수동 실행 8963 도 같은 184,532건 완료. 락은 소유자가 아니라 해제 생략 | `status=SUCCESS rows=184532 elapsed=1008s` / `lock=release_skipped` |
| 03:15:31 | `datamart-counts.csv:24` | 기준일 09-15 적재 건수 = 369,064 (= 184,532 × 2) | `2026-09-15,load_datamart,369064,2026-09-16T03:15:31+09:00` |
| **03:30:04** | `batch-job.log:113` | **무시된 경고** — 다음 job 이 입력 건수 불일치를 감지했지만 그대로 진행 | `input_rows=369064 note="input_rows differs from extract_submissions …"` |
| 03:33:42 | `batch-job.log:114`, CSV 25행 | build_report_cache 가 2배 입력으로 완료(출력 1,271행) | `status=SUCCESS rows=1271 input_rows=369064` |
| 07:00:04 | `batch-job.log:115` | 데이터 품질 점검이 +99% 편차 감지, 온콜A 통보 (첫 외부 감지) | `[dq-check@MON-1] … rows=369064 avg_7d=184617 deviation=+99%` |

- 로그로 확인 불가: 07:00 이후 이중 적재분 삭제 · 재적재 여부, 리포트 캐시가 사용자에게 노출됐는지, 운영자A 가 재실행 전 진행률 로그를 봤는지.

## 3. 가설과 검증

| # | 계층 | 가설 | 지지 근거 | 반증 조건(이것이 보이면 틀린 것) | 확인 방법(실행할 명령) | 실행 결과(숫자) | 로그 판정 |
|---|---|---|---|---|---|---|---|
| H1 | 운영 · 설정 | force 수동 재실행이 락을 우회해 cron 실행과 동시에 돌고, 둘 다 append 로 같은 기준일을 적재해 2배가 됐다 | :99-100 `held_by … ignored` · `bypassed`, :89 · :101 `mode=append`, 두 SUCCESS 동일 rows | 09-15 기준일 load SUCCESS 가 1건뿐이거나, 두 실행 rows 합이 CSV 값과 다르다 | `grep -c 'date=2026-09-15.*load_datamart.*status=SUCCESS' batch-job.log` ; `echo $((184532*2))` | SUCCESS 2건(edf5 · 8963), 184,532 × 2 = 369,064 = CSV 24행 값 | **채택** |
| H2 | 앱(배치 코드) | load_datamart 한 번의 실행 안에서 원천을 두 번 읽어 2배가 됐다 | 결과가 정확히 2배 | 한 실행의 rows 가 평소 범위(18.2~18.6만)이다 | `grep 'load_datamart.*status=SUCCESS' batch-job.log` | 각 실행 184,532(정상 범위), 09-16·09-17 기준일도 184,895 · 184,551(pipeline-samples CSV) | **기각** |
| H3 | DB · 원천 데이터 | replica lag 로 extract 가 중복 · 과다 추출해 load 가 커졌다 | :86 replica lag 412s, extract 1668s | extract rows 가 평소 범위(41,203~43,665)이다 | `grep 'extract_submissions.*status=SUCCESS' batch-job.log` | 41,573 (정상 범위) | **기각(원인)** / **배경 요인**(지연 → SLA 알림 → 오판의 출발점) |
| H4 | 스케줄러 | cron 이 load_datamart 를 두 번 띄웠다 | 같은 날 START 2건 | 두 번째 START 의 trigger 가 cron 이 아니다 | `grep -c 'load_datamart.*status=START.*trigger=cron' batch-job.log` (09-16 분) · `grep -c 'trigger=manual'` | 09-16 cron START 1건, manual 1건(:100) | **기각** |

### 코드 · 설정 대조

- `grep -rlE 'load_datamart|batchctl|daily_submission_fact|lock_path'` 결과, 로그 · 샘플 데이터(`incident-logs/`, `pipeline-samples/`)와 실습 템플릿(`templates/CLAUDE.data-pipeline.md:24,27`, 명령 예시뿐) 외에는 일치가 없다. **관련 배치 코드 · 설정 파일은 이 저장소에서 찾지 못했다.**
- 대신 로그 안의 값이 "설정" 역할을 한다: `schedule="0 2 * * *"`(:88), `mode=append`(:89, :101), `force=true`(:97-98), `lock=held_by … action=ignored reason="--force"`(:99), `lock=bypassed`(:100), `lock=release_skipped reason="not owner (bypassed)"`(:111), `elapsed … exceeds p95 17m`(:94), `sla=02:45`(:95).
- 이 값들로 보면 H1 의 두 조건(① force 가 락을 무시한다, ② 적재가 기준일 단위로 멱등이 아니다)이 모두 로그로 확인된다.

### 확신도

- **메커니즘(락 우회 + append 이중 적재): 높음** — 두 실행 rows 합이 CSV 값과 1건도 틀리지 않고 일치한다.
- **계기(SLA 알림을 보고 한 force 재실행): 높음** — :97 사유 문구에 그대로 남아 있다.
- **첫 실행이 왜 느렸는가: 낮음** — extract 지연 사유(replica lag)는 :86 에 있지만, load 단계 자체의 지연 사유는 로그에 없다. 로그로 확인 불가.

## 4. 원인

- **직접 원인**: 02:58:42 `force=true` 수동 재실행이 진행 중인 cron 실행의 락을 무시(:99)하고 시작(:100)했고, 두 실행이 모두 `mode=append`(:89, :101)로 기준일 2026-09-15 를 적재했다.
- **배경 원인**
  - 적재가 기준일 단위로 멱등하지 않다(append, 기준일 파티션 삭제 · 교체 없음).
  - `--force` 가 "실패한 실행 다시 돌리기"가 아니라 "락 무시"로 동작한다. 소유자 아닌 실행은 락을 해제도 하지 않는다(:111).
  - 원천 DB replica lag(:86)로 extract 가 2.3배 걸렸고 load 도 평소보다 느려 SLA(02:45)를 넘겼다.
- **악화 요인**
  - SLA 알림(:95)이 고정 시각 기준이라 `last_status=RUNNING` 이면서 진행률이 오르는 실행도 "미완료"로 알렸고, 운영자가 이를 "멈춤"으로 읽었다.
  - 03:30:04 build_report_cache 의 입력 불일치 경고(:113)가 실행을 막지 않아 리포트 캐시까지 오염됐다.
  - 데이터 품질 점검이 07:00 한 번뿐이라 이중 적재 확정(03:15) 뒤 약 3시간 45분 동안 감지되지 않았다.

## 5. 근거 로그 줄

| 근거 | 출처 |
|---|---|
| 선행 지연(replica lag) | `batch-job.log:86-87` |
| cron 실행 시작 · append | `batch-job.log:88-89` |
| 실행이 계속 진행 중이었음 | `batch-job.log:91-93,96,103,105` |
| SLA 알림이 RUNNING 상태를 보고 | `batch-job.log:95` |
| force 재실행 요청 · 락 무시 · 락 우회 시작 | `batch-job.log:97-101` |
| 두 실행 SUCCESS 동일 rows | `batch-job.log:108,110` |
| 락 해제 생략 | `batch-job.log:111` |
| 2배 적재 확정 | `datamart-counts.csv:24` |
| 무시된 입력 불일치 경고 · 오염된 캐시 | `batch-job.log:113-114`, `datamart-counts.csv:25` |
| 첫 외부 감지 | `batch-job.log:115` |

## 6. 수정안(제안만 — 코드 · 설정은 이 저장소에 없어 적용하지 않음)

1. **force 의 의미 분리**: 같은 job·date 의 실행이 `RUNNING` 이면 `--force` 라도 시작을 거부한다. 진행 중 실행을 대체하려면 먼저 그 실행을 명시적으로 중단(`cancel run_id`)하게 한다.
2. **멱등 적재**: `mode=append` 대신 기준일 파티션 교체(삭제 후 삽입 또는 `INSERT … ON DUPLICATE KEY`/MERGE)로 바꿔, 같은 기준일을 두 번 돌려도 결과가 같게 한다.
3. **하류 job 의 차단**: build_report_cache 의 `input_rows differs` 를 WARN 에서 실패(또는 보류)로 올린다.
4. **SLA 알림 문구에 진행률 포함**: `last_status=RUNNING progress=64% (+13%p/12m)` 처럼 "느림"과 "멈춤"을 구분할 수 있게 한다.
5. 즉시 복구(운영 판단 필요): 기준일 2026-09-15 의 `dm.daily_submission_fact` 에서 run_id `ldm-20260916-0258-8963` 분을 제거하고 build_report_cache 를 다시 돌린다(행에 run_id 컬럼이 있는지는 로그로 확인 불가).

## 7. 재발 방지

- 운영 절차: 재실행 전 "같은 job·date 의 RUNNING 실행 유무 · 최근 progress 증가 여부" 확인을 체크리스트로 둔다. progress 가 오르고 있으면 재실행하지 않는다.
- `batchctl` 의 force 재실행은 2인 확인 또는 사유 + 진행 중 run_id 중단 확인을 요구한다.
- 적재 job 은 멱등을 기본 요건으로 하고, 비멱등 job 에는 force 옵션을 제공하지 않는다.
- 데이터 품질 점검을 07:00 한 번이 아니라 각 적재 job 직후에도 돌린다.

## 8. 모니터링 항목

ERROR 줄이 0건이므로 "첫 ERROR 대비"는 **ERROR 없음**으로 두고, 비교 기준을 ① 이중 적재 시작(02:58:42, :100) ② 첫 외부 감지(07:00:04 dq-check, :115)로 잡는다.

| # | 항목 | 임계값 | 이번에 울렸을 시각 | 이중 적재 시작 대비 | 07:00 dq-check 대비 |
|---|---|---|---|---|---|
| M1 | 같은 job·date 동시 RUNNING 실행 수 | ≥ 2 | 02:58:42 (:100) | 즉시(0분) | 4시간 1분 앞 |
| M2 | 락 이상 이벤트 `lock=held_by\|bypassed\|release_skipped` | ≥ 1 | 02:58:42 (:99) | 즉시(0분) | 4시간 1분 앞 |
| M3 | 같은 job·date 의 START 수 / `trigger=manual` 수 | START ≥ 2 또는 manual ≥ 1 | 02:58:42 (:100) | 즉시(0분) | 4시간 1분 앞 |
| M4 | 적재 건수 vs 7일 평균 편차 | ±30% 초과 | 03:15:31 (CSV 24행, +99.9%) | 17분 뒤(적재 완료 시) | 3시간 45분 앞 |
| M5 | load/extract 건수 비율 | 평소 4.3~4.5배, 6배 초과면 이상 | 03:15:31 (369,064 / 41,573 = 8.9배) | 17분 뒤 | 3시간 45분 앞 |
| M6 | 하류 job 의 `input_rows differs` 경고 | ≥ 1 | 03:30:04 (:113) | 31분 뒤 | 3시간 30분 앞 |
| M7 | 실행 소요가 p95 초과 **+ 진행률 증가 여부** | p95(17분) 초과 시, 최근 15분 progress 증가 없으면 "멈춤", 있으면 "지연" | 02:40:03 (:94) — 이번엔 "지연"으로 판정됐어야 함 | 18분 앞(재실행 판단 근거 제공) | 4시간 20분 앞 |
| M8 | 원천 replica lag | > 300s | 01:44:07 (:86, 412s) | 1시간 14분 앞(선행 신호, 장애 직접 신호 아님) | 5시간 16분 앞 |

## 9. 확인하지 못한 것

| 무엇 | 왜 못 했나 | 무엇을 남겨야 알 수 있었나 |
|---|---|---|
| load_datamart 첫 실행이 느린 이유 | load 단계에는 지연 사유 줄이 없다(extract 만 :86) | load 의 step 별 소요(read · write · commit), 대상 DB 대기 이벤트 |
| 적재 행에 run_id 가 남는지(복구 가능성) | 스키마 · 코드가 저장소에 없음 | 대상 테이블 DDL, 적재 SQL |
| `--force` · 락 동작이 설계인지 결함인지 | 배치 러너 소스 · 설정이 저장소에 없음 | 러너의 락 · force 처리 코드, batchctl 사용 문서 |
| 운영자A 가 재실행 전 진행률을 봤는지 | 운영 콘솔 조회 기록 없음 | batchctl 조회 이력, 온콜 채널 대화 |
| 07:00 이후 복구 조치와 사용자 노출 범위 | 로그가 07:00:04 에서 끝남 | 복구 작업 로그, 리포트 캐시 조회 로그 |

## 3회차 연결 메모

8절 모니터링 항목 중 **매일 아침 파일만 읽어서** 판정할 수 있는 것을 골랐다. 실시간 상태가 필요한 M1(동시 RUNNING 수)은 아침 점검으로는 사후에만 보이므로 M3 으로 대신하고, M8(replica lag)은 원천 DB 지표가 필요해 **3회차에 확인**으로 둔다.

- 실제 파일(`ls pipeline-samples/` 로 확인): `pipeline-samples/batch-logs/2026-09-08.log` ~ `2026-09-17.log`(10개), `pipeline-samples/datamart-counts.csv`(머리글 `date,job,row_count,loaded_at`, 50줄, job 5개).
- **"오늘 로그" 는 `pipeline-samples/batch-logs/2026-09-15.log`** 로 잡았다. 파일 이름은 기준일이고 줄 시각은 다음날(2026-09-16 00:00~07:00)이다. 이번 장애와 같은 사건이며, 아래 줄 번호는 이 파일 기준이다(27 p95 경고, 28 SLA, 31 락 무시, 32 manual START, 43 release_skipped, 45 입력 불일치, 47 dq-check).

### 점검 항목

| # | 무엇을 보는가 | 어느 파일에서 | 이상 판정 기준 | 이상일 때 /rca 로 넘길 정보 |
|---|---|---|---|---|
| D1 | 같은 job·date 의 START 수, `trigger=manual` 수 (8절 M3) | `pipeline-samples/batch-logs/<기준일>.log` | job·date 별 `status=START` ≥ 2 또는 `trigger=manual` ≥ 1 | job, date, 모든 run_id 와 trigger, operator(별칭), START 줄 번호 |
| D2 | 락 이상 이벤트 (M2) | `pipeline-samples/batch-logs/<기준일>.log` | `lock=(held_by\|bypassed\|release_skipped)` ≥ 1 | 해당 줄 번호, 락을 쥔 run_id 와 우회한 run_id, `reason=` 값 |
| D3 | job 별 적재 건수 vs 직전 7일 평균 (M4) | `pipeline-samples/datamart-counts.csv` | 편차 ±30% 초과 | date, job, row_count, 7일 평균, 편차 %, CSV 행 번호, `loaded_at` |
| D4 | load_datamart / extract_submissions 건수 비율 (M5) | `pipeline-samples/datamart-counts.csv` | 비율 6배 초과(평소 4.3~4.5배) | 두 job 의 row_count 와 비율, 같은 날 D1 · D2 결과 |
| D5 | 하류 입력 불일치 경고 (M6) | `pipeline-samples/batch-logs/<기준일>.log` | `input_rows differs` ≥ 1 | 경고 줄 번호, `input_rows` 값, 상류 job 의 SUCCESS rows |
| D6 | 소요 p95 초과 + 진행률 증가 여부 (M7) | `pipeline-samples/batch-logs/<기준일>.log` | `exceeds p95` 경고가 있으면 같은 run_id 의 progress 시계열을 보고 "지연"(증가 중) / "멈춤"(15분 이상 정체)로 분류. **"지연" 이면 재실행 금지 표시** | run_id, 경고 줄 번호, progress 시각 · % 목록, 최종 elapsed |
| D7 | 계획된 job 대비 SUCCESS 누락 | `pipeline-samples/batch-logs/<기준일>.log`(`day_open … planned=` 줄) + `pipeline-samples/datamart-counts.csv` | `planned=` 의 job 중 SUCCESS 가 없는 job ≥ 1, 또는 START 수 ≠ SUCCESS 수 | 누락 job 이름, 마지막 줄(START/RUNNING) 번호와 progress, CSV 해당 행 유무 |
| — | 원천 replica lag (M8) | 3회차에 확인 | 3회차에 확인 | 3회차에 확인 |

### 오늘 로그(`2026-09-15.log`)로 검증

| # | 오늘 잡았을지 | 근거(실행 결과) |
|---|---|---|
| D1 | **잡음** | START 6건(평소 5건), `trigger=manual` 1건 — 32행 02:58:42 |
| D2 | **잡음** | 락 이상 3건 — 31행(held_by · ignored), 32행(bypassed), 43행(release_skipped) |
| D3 | **잡음** | 2026-09-15 load_datamart 369,064 vs 7일 평균 184,617 → +99.9% |
| D4 | **잡음** | 369,064 / 41,573 = 8.9배 (09-14 는 186,058 / 42,094 = 4.4배) |
| D5 | **잡음** | 45행 03:30:04 `input_rows differs` 1건 |
| D6 | **잡음(분류까지)** | 27행 02:40:03 p95 초과 경고 1건. 같은 run_id 의 progress 가 25→38→51→64% 로 약 12분마다 올라 "지연" 으로 분류됐을 것 — 재실행 금지 신호가 됐어야 함 |
| D7 | **못 잡음** | 오늘은 START 6 = SUCCESS 6, planned 5개 모두 SUCCESS — 이중 실행은 누락이 아니라서 이 기준에 안 걸린다. 대신 다른 날 이상을 잡는다: `2026-09-09.log` 는 START 5 · SUCCESS 4(build_item_stats SUCCESS 없음, 4행 progress 25% 가 마지막), CSV 에도 2026-09-09 build_item_stats 행이 없다 |

- 덤으로 본 것(이번 장애와 무관, 3회차에 확인): CSV 2026-09-17 build_item_stats 7,284 건은 평소(12,414~12,490)보다 약 41% 적어 D3 에 걸린다.
- 오늘 기준 D1 · D2 · D5 는 07:00 dq-check 보다 3시간 30분~4시간 앞서 로그에 이미 있었다. 아침 점검으로 옮기면 "감지 시각"은 점검 시각이 되지만, /rca 로 넘길 run_id · 줄 번호가 한 번에 모인다.

마스킹 적용: IP 4건 · 이메일 5건 · 학생 식별자 1건 · 토큰 1건 · 비밀번호 0건
