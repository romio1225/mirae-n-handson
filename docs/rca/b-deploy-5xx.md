# RCA — b-deploy-5xx (item-bank-api v1.4.2 배포 후 재배포 API 500)

> 작성 범위: 요청에 따라 **1. 수집 범위 · 2. 타임라인 · 3. 가설 표(로그 판정까지)** 만 작성했다. 코드 · 설정 · 스키마 대조(검증 단계)와 요약 · 4절 이후(원인 · 근거 로그 줄 · 수정안 · 재발 방지 · 모니터링 · 확인하지 못한 것)는 작성하지 않았다.
> 대상: `incident-logs/b-deploy-5xx/` (원본 로그는 수정하지 않음)

## 1. 수집 범위

### 1-1. 파일별 개요

| 파일 | 줄 수 | 첫 시각 | 끝 시각 | 시각 형식 | 타임존 |
|---|---|---|---|---|---|
| `app.log` | 2,938 | 2026-09-17 09:00:04.878 (`app.log:1`) | 2026-09-17 14:39:57.905 (`app.log:2938`) | ISO-8601 `2026-09-17T09:00:04.878+09:00` | `+09:00` 명시 → KST |
| `nginx-access.log` | 20,365 | 17/Sep/2026:09:00:00 (`nginx-access.log:1`) | 17/Sep/2026:14:39:58 (`nginx-access.log:20365`) | combined `[17/Sep/2026:09:00:00 +0900]` | `+0900` 명시 → KST |
| `deploy-history.md` | 39 | 2026-09-01 18:02:47 (v1.3.9) | 2026-09-17 13:42:31 (v1.4.2 배포 완료) | `YYYY-MM-DD HH:MM:SS` | 본문에 "시각은 모두 KST" (`deploy-history.md:3`) |

- 세 파일 모두 타임존이 명시돼 있어 변환 없이 KST 한 시간축으로 맞췄다. 교차 확인: 같은 요청이 `nginx-access.log:3150`(09:52:17, `POST /api/distributions/41/redistribute` 500)과 `app.log:194`(09:52:17.335, 같은 경로 ERROR)에 같은 초로 찍혀 있어 두 로그 사이 시각 오차는 1초 미만이다.
- `app.log` 수준별: INFO 1,194 · WARN 35 · ERROR 46 (`awk '{print $2}' app.log | sort | uniq -c`). 나머지 줄은 스택 트레이스.
- `nginx-access.log` 에는 응답 시간 필드가 없다(combined 형식). 지연 여부는 로그로 확인 불가.
- `app.log` 의 기동 줄은 1개뿐이다(`app.log:1245`, v1.4.2). 09:00 이전 버전의 기동 기록은 이 묶음에 없다.
- 참고: `deploy-history.md:20` 에 배포 API 인증 헤더가 원문으로 들어 있다(`Bearer <token>` 으로 가림). `app.log` 의 재배포 사유 문자열에도 학생 식별자가 그대로 기록돼 있다(예: `app.log:1238`). 리포트 발췌에서는 모두 가렸다.

### 1-2. 10분 단위 상태 코드 건수 (nginx)

`awk` 로 `nginx-access.log` 의 10분 버킷별 상태 코드를 셌다.

| 구간(KST) | 2xx | 4xx | 5xx | 비고 |
|---|---|---|---|---|
| 09:00–13:39 (28개 버킷) | 479–644 | 15–40 | 0–3 | 5xx 합계 7건(09:50 3 · 10:30 1 · 11:10 3), 전부 `/api/distributions/41` |
| 13:40 | 593 | 21 | **11** | 502 8건 + 500 3건 |
| 13:50 | 513 | 25 | **11** | |
| 14:00 | 568 | 38 | **7** | |
| 14:10 | 622 | 37 | **6** | |
| 14:20 | 566 | 33 | **6** | |
| 14:30 | 545 | 30 | **6** | 로그 끝(14:39:58)까지 계속 |

- 5xx 전체 54건 = 배포 전 500 7건 + 재기동 중 502 8건 + 배포 후 500 39건(`grep -c '" 500 '` 46 · `grep -c '" 502 '` 8).
- 전체 요청량은 버킷당 2xx 479–644 로 장애 전후 차이가 없다. 분당 요청 수도 배포 직전 13:30–13:40 평균 66.9건/분, 배포 후 13:42–13:49 평균 60.0건/분이다.

### 1-3. 장애 구간과 분석 대상 구간

- 장애 구간(제안): **13:41:51 ~ 14:34:45 KST** (첫 502 `nginx-access.log:16865` ~ 마지막 500 `nginx-access.log:20084`). 로그 끝까지 해소 기록이 없다.
- 분석 대상 구간: **13:30:00 ~ 14:39:58** (앞 약 12분 여유, 뒤는 로그 끝).
- 선행 구간(별도): **09:52:17 ~ 11:18:02** — 같은 예외(`Assignment` 9 없음)가 배포 전부터 7건 있어 따로 본다(`app.log:194-763`, `nginx-access.log:3150-8468`).

## 2. 타임라인

모든 시각은 KST. 원문 발췌는 80자 이내로 자르고 민감값을 가렸다.

| 시각(KST) | 출처 | 사건 요약 | 원문 발췌 |
|---|---|---|---|
| 09:07:01–13:41:20 | `app.log:28`, `:59` 외 / `nginx-access.log:931` 외 | 배포 전 재배포 42 · 43 · 44 등 모두 성공. 42 → 5건 · 43 → 13건 · 44 → 17건 200 | `redistributed distribution 43 (assignment 6, class 1) reason=학생B 결석…` |
| 09:06–13:15 | `app.log:24-1166` (WARN 33건) | 마감 과제(배포 1 · 2 · 3) 재배포 409. 평소 패턴 | `state conflict: 마감된 과제는 재배포할 수 없습니다: distributionId=3` |
| **09:52:17** | `app.log:194`, `:224` / `nginx-access.log:3150` | **조용한 선행 신호.** 배포 41 재배포 첫 500. 원인 `Assignment` id 9 없음. 스택은 `loadOrThrow` → `Controller.java:38` (`app.log:205`, `:214`) | `unhandled exception on /api/distributions/41/redistribute` / `Entity …Assignment with identifier value 9 does not exist` |
| 09:52:30–09:52:41 | `app.log:232`, `:270` / `nginx-access.log:3162`, `:3174` | 41 재배포 재시도 2건 모두 500 (교사단말A) | `POST /api/distributions/41/redistribute HTTP/1.1" 500 176` |
| 10:31:05 | `app.log:451`, `:462` / `nginx-access.log:5563` | 41 단건 조회도 500. 같은 예외, `getDistribution` → `loadOrThrow` | `unhandled exception on /api/distributions/41` |
| 11:17:40–11:18:02 | `app.log:655-763` / `nginx-access.log:8442-8468` (3건) | 41 재배포 다시 3건 500. 배포 전 5xx 는 여기까지 7건, 전부 배포 41 | `POST /api/distributions/41/redistribute … 500 176` |
| 13:38 (배포 승인) | `deploy-history.md:21` | v1.4.2 승인. 서버 로그로는 확인 불가 | `승인: 승인자A (13:38)` |
| 13:40:05 (배포 시작) | `deploy-history.md:7` | v1.4.2 배포 시작. 변경: 재배포 응답에 학급 배포 이력 포함, `DistributionService.listByClass(classId)` 호출 추가 (`deploy-history.md:15-16`). 서버 로그에는 13:40:05 에 대응하는 줄 없음 → 로그로 확인 불가 | `v1.4.2 \| 2026-09-17 13:40:05 \| 2026-09-17 13:42:31 \| 개발자A` |
| 13:41:20 | `app.log:1238` / `nginx-access.log:16829` | 구버전에서 마지막 43 재배포 성공(class 1). 응답 183바이트 | `redistributed distribution 43 (assignment 6, class 1) reason=학생A 요청…` |
| 13:41:48–13:41:50 | `app.log:1240-1244` | 구버전 graceful shutdown, itembank-pool 종료 | `Commencing graceful shutdown. Waiting for active requests to complete` |
| **13:41:51–13:41:59** | `nginx-access.log:16865-16872` (8건) | 재기동 중 502 8건(학생 조회 · probe 포함). 단일 호스트 재기동(무중단 아님, `deploy-history.md:18`) | `GET /api/units/M5-2/items?student=STU-xxxx HTTP/1.1" 502 157` |
| 13:41:55.902 | `app.log:1245` | v1.4.2 기동 시작 | `Starting ItemBankApplication v1.4.2 using Java 21.0.4 with PID 1` |
| 13:42:00.304 | `app.log:1264` / `nginx-access.log:16873` | 기동 완료(4.9초). 13:42:01 부터 200 복귀 | `Started ItemBankApplication in 4.921 seconds` |
| 13:42:10 | `app.log:1266` / `nginx-access.log:16884` | **조용한 반례.** 신버전에서 배포 7(class 3) 재배포는 200 | `redistributed distribution 7 (assignment 5, class 3) reason=보강 수업 후 재배포` |
| 13:42:00–13:46:38 | `app.log:1265-1273` | **기록 공백(약 4분 38초).** 신버전 기동 후 class 1 재배포 요청이 없어 오류도 없음 | (해당 구간 ERROR 0건) |
| **13:46:38.107** | `app.log:1274` | 배포 42(class 1) 재배포 처리 로그가 먼저 찍힘. 6ms 뒤 ERROR | `redistributed distribution 42 (assignment 4, class 1) reason=보강 수업 후 재배포` |
| **13:46:38.113** | `app.log:1275`, `:1286`, `:1294`, `:1304` / `nginx-access.log:17155` | **배포 후 첫 500.** 같은 예외(`Assignment` 9 없음)지만 스택이 `listByClass(DistributionService.java:38)` → `redistribute(DistributionController.java:40)` | `unhandled exception on /api/distributions/42/redistribute` |
| 13:46:58–13:47:08 | `app.log:1312-1351` / `nginx-access.log:17170`, `:17181` | 42 재시도 2건 500. 매번 `redistributed …` 줄이 먼저 찍힘 | `redistributed distribution 42 (assignment 4, class 1) reason=학생B 결석…` |
| 13:50:00 | `app.log:1393` | 배포 8(class 2) 재배포 성공 | `redistributed distribution 8 (assignment 6, class 2) reason=학생B 결석…` |
| 13:50:12–13:50:43 | `app.log:1396-1512` / `nginx-access.log:17365-17400` (4건) | 배포 43(class 1) 재배포 첫 500, 4건 연속 | `POST /api/distributions/43/redistribute … 500 176` |
| 13:56:43 | `app.log:1754` / `nginx-access.log:17734` | 배포 44(class 2) 재배포 200. 응답 크기 640바이트(배포 전 188 · 195바이트, `nginx-access.log:3060`, `:3175`) → 신버전 응답 형식(이력 포함)이 실제로 나감 | `POST /api/distributions/44/redistribute HTTP/1.1" 200 640` |
| 13:46:38–14:34:45 | `app.log:1275-2887` (ERROR 39건) / `nginx-access.log:17155-20084` (500 39건) | 42 → 19건 · 43 → 20건 모두 500. 같은 교사단말A가 2–4회씩 재시도하는 묶음이 13개 | `unhandled exception on /api/distributions/43/redistribute` |
| 13:59:30, 14:24:12 등 | `app.log:1837`, `:2550`, `:2679`, `:2925` | 마감 과제 409 는 배포 후에도 평소대로 | `state conflict: 마감된 과제는 재배포할 수 없습니다: distributionId=3` |
| 14:21:02, 14:22:46 | `app.log:2420`, `:2542` | 배포 5(class 3) 재배포 200 | `redistributed distribution 5 (assignment 3, class 3) reason=학생B 결석…` |
| 14:34:45 | `app.log:2887` / `nginx-access.log:20084` | 마지막 500. 로그 끝(14:39:58)까지 롤백 · 재기동 기록 없음. 롤백 여부는 로그로 확인 불가 | `POST /api/distributions/43/redistribute … 500 176` |

정리:
- 가장 시끄러운 오류(배포 후 500 39건)보다 앞선 조용한 줄은 **09:52:17 의 배포 41 오류**(`app.log:194`)다. 같은 `Assignment` 9 없음 예외가 배포 약 3시간 50분 전부터 있었다.
- 배포 직후가 아니라 **4분 38초 뒤**(13:46:38)에 첫 500 이 났다. 그 사이 class 1 재배포 요청이 없었다(`app.log:1265-1273`).
- 배포 후 실패는 **class 1(배포 42 · 43)에만** 있다. 같은 시간대 class 2 · 3 재배포(44 · 5 · 7 · 8) 6건은 모두 200 이다.
- 실패한 요청마다 `redistributed …` INFO 가 ERROR 보다 먼저 찍혔다(예: `app.log:1274` → `:1275`). 재배포 저장이 커밋됐는지 롤백됐는지는 로그로 확인 불가.

## 3. 가설과 검증

아래 표는 로그로 반증을 시도한 결과까지다. 코드 · 설정 · 스키마 대조와 최종 판정(채택 · 기각 · 악화 요인 · 판단 불가, 확신도)은 이번 범위에서 하지 않았다.

| # | 계층 | 가설 | 지지 근거 | 반증 조건(이것이 보이면 틀린 것) | 확인 방법(실행할 명령) | 실행 결과(숫자) | 로그 판정 |
|---|---|---|---|---|---|---|---|
| H1 | 배포/코드 | v1.4.2 가 재배포 뒤 `listByClass(classId)` 로 학급 전체 배포 이력을 읽게 되면서, class 1 이력 안의 깨진 행(없는 `Assignment` 9 를 참조)을 로드하다 예외가 나 500 이 됐다 | 배포 후 500 스택에 `listByClass` · `Controller.java:40` (`app.log:1286`, `:1294`). 변경 내용(`deploy-history.md:16`). 실패는 class 1 에만 | ① 배포 후 500 스택에 `listByClass` 가 없는 건이 있다 ② 배포 후 class 1 재배포가 200 인 건이 있다 ③ 배포 후 class 2 · 3 재배포가 500 이다 | `grep -c 'listByClass(DistributionService' app.log`; `grep -n 'redistribute' nginx-access.log \| awk …(13:42 전후 · id · 상태 집계)`; `grep -n 'redistributed distribution' app.log \| awk …` | ① `listByClass` 39 / 배포 후 ERROR 39 ② class 1(42 · 43) 배포 후 200 0건 / 500 39건 ③ class 2 · 3(44 · 5 · 7 · 8) 배포 후 6건 모두 200 | 반증 안 됨(유지). 단 배포 41 이 class 1 소속인지는 로그로 확인 불가 |
| H2 | DB/데이터 | `Assignment` id 9 가 없는데 이를 참조하는 배포 행(최소 배포 41)이 배포 전부터 있었다. v1.4.2 이전에는 그 행을 직접 열 때만 실패했다 | 09:52 부터 41 조회 · 재배포 7건 500, 모두 같은 예외(`app.log:224`). 46건 예외 메시지가 모두 `identifier value 9` | ① 배포 전에 `Assignment` 9 예외가 0건 ② 배포 전 41 요청이 200 인 건이 있다 ③ 예외 메시지에 9 외 다른 id 가 있다 | `grep -n 'unhandled exception' app.log \| awk -F: '$1<1245'`; `grep 'distributions/41' nginx-access.log \| awk '{print $9}' \| sort \| uniq -c`; `grep 'Entity \`' app.log \| sort \| uniq -c` | ① 배포 전 7건(09:52:17 첫) ② 41 요청 7건 모두 500, 200 0건 ③ 92줄(46건 × 2) 모두 id 9 | 반증 안 됨(유지). 행이 언제 · 왜 깨졌는지는 로그로 확인 불가 |
| H3 | 배포 방식 | 단일 호스트 재기동(무중단 아님)으로 재기동 동안 502 가 났다 | `deploy-history.md:18`. shutdown `app.log:1240` ~ 기동 완료 `app.log:1264` | 502 가 13:41:48–13:42:00 밖에도 있다 | `grep -n '" 502 ' nginx-access.log` | 8건, 모두 13:41:51–13:41:59 (`nginx-access.log:16865-16872`). 구간 밖 0건 | 반증 안 됨. 다만 13:42:01 이후 지속된 500 39건은 설명하지 못함 → 별개의 일시 영향 |
| H4 | 트래픽 | 배포 시점 요청 급증 또는 특정 클라이언트의 재시도 폭주가 5xx 를 만들었다 | 배포 후 500 이 한 교사단말에서만 2–4회씩 반복 | ① 배포 전후 분당 요청 수가 비슷하다 ② 5xx 가 재시도 없이 첫 요청부터 난다 | `awk '$4 ~ /13:[34][0-9]/ {…분 단위}' nginx-access.log \| uniq -c`; 10분 버킷 집계 | ① 13:30–13:40 평균 66.9건/분 vs 13:42–13:49 60.0건/분. 10분 버킷 2xx 479–644 로 평탄 ② 13:46:38 첫 요청부터 500 (`nginx-access.log:17155`) | 원인으로서는 반증됨. 재시도는 같은 오류를 반복시켰을 뿐(악화 여부는 저장 커밋 여부 확인 필요, 로그로 확인 불가) |
| H5 | DB/연결 | DB 연결 풀 고갈 · 타임아웃으로 조회가 실패했다 | 재기동 시 itembank-pool 재생성(`app.log:1243-1260`) | 연결 관련 오류(`Connection is not available`, timeout)가 0건이고, 예외가 엔티티 없음 하나뿐이다 | `grep -ciE 'Connection is not available\|timeout\|SQLTransient' app.log` | 0건. 예외 46건 모두 `FetchNotFoundException`(엔티티 없음) | 반증됨 |

로그 판정 요약: 유지 2개(H1 · H2), 부분 유지 1개(H3, 502 8건만 설명), 반증 2개(H4 원인으로서 · H5). H1 · H2 의 코드 · 스키마 대조(4단계 검증)는 하지 않았다.

---

마스킹 적용: IP 2건 · 이메일 2건 · 학생 식별자 6건 · 토큰 1건 · 비밀번호 0건
