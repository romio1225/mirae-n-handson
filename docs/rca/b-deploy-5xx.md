# RCA — b-deploy-5xx (item-bank-api v1.4.2 배포 후 재배포 API 500)

## 요약

- **무엇이**: 학급 1 의 과제 재배포 API(`POST /api/distributions/42|43/redistribute`)가 매번 500 을 돌려줬다. 39건, 모두 교사단말A 한 곳의 요청이다. 같은 시간대 학급 2 · 3 재배포 6건은 200 이었다. 따로, 배포 중 재기동 9초 사이에 502 8건이 났다.
- **언제**: 2026-09-17 13:46:38 KST 첫 500(`app.log:1275`)부터 14:34:45 마지막 500(`app.log:2887`)까지. v1.4.2 기동 완료(13:42:00, `app.log:1264`) 4분 38초 뒤에 시작했고, 로그 끝(14:39:58)까지 해소 기록이 없다. 같은 예외가 배포 3시간 48분 전인 09:52:17(`app.log:194`)부터 배포 41 에서 7건 있었다.
- **원인**: v1.4.2 가 재배포 직후 `listByClass(classId)` 로 학급 전체 배포 이력을 읽도록 바뀌었다(`DistributionController.java:40`). 학급 1 이력 안에 없는 과제(`Assignment` id 9)를 가리키는 배포 행이 원래 있었고, 이 행을 `@EntityGraph` 로 함께 읽다가 `FetchNotFoundException` 이 났다. 이 예외는 `GlobalExceptionHandler.java:59-63` 의 일반 핸들러로 가서 500 이 됐다. 직접 원인은 v1.4.2 의 새 호출 경로, 배경 원인은 그 전부터 있던 고아 데이터다.
- **확신**: 메커니즘은 **높음**(스택 39건이 모두 `listByClass` → `Controller.java:40` 이고, 코드와 줄번호가 맞는다). 계기 중 "고아 행이 학급 1 에 있다"도 **높음**이다. 다만 고아 행이 배포 41 인지, 언제 · 왜 생겼는지는 **낮음/판단 불가**다(DB 미확인).
- **악화**: 재배포 쓰기와 이력 읽기가 서로 다른 트랜잭션이라, 500 을 받은 39건도 코드상 재배포 저장은 커밋됐다(DB 미확인). 교사는 실패를 보고 2–4회씩 재시도했다.
- **조치 방향(제안)**: v1.4.1 롤백 또는 고아 행 정리가 당장의 조치다. 근본 조치는 쓰기와 이력 읽기를 한 서비스 메서드 · 한 트랜잭션으로 묶고, 참조 무결성 점검과 배포 전 운영 유사 데이터 검증을 추가하는 것이다.
- 이번 추가 범위: 요약, 3-1 코드 · 설정 대조, 3-2 판정, 4–9절. 바로 아래 "작성 범위" 문구와 3절 머리말 · 3절 끝 요약 줄은 1차 작성(1–3절) 당시 그대로 두었다.

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

### 3-1. 코드 · 설정 대조

`deploy-history.md` 의 v1.4.2 변경 항목(`deploy-history.md:7`, `:15-16`)을 출발점으로 `modern/api` 의 현재 코드와 대조했다(읽기만 함). 저장소 코드가 v1.4.2 배포본과 같은지는 저장소 안에서 확인할 수 없다. 대신 로그 스택의 줄번호(`DistributionService.java:38` · `:51` · `:65`, `DistributionController.java:38` · `:40`)가 현재 코드의 해당 줄과 모두 맞는다.

| # | 대조 항목 | 코드 · 설정 근거 | 로그와의 대응 | 결과 |
|---|---|---|---|---|
| C1 | v1.4.2 변경 지점 | `DistributionController.java:38` 재배포(`distributionService.redistribute`) 다음에 `:40` `distributionService.listByClass(redistributed.classId())` 호출. `:39` 주석에 "v1.4.2 — 재배포 응답에 학급 배포 이력(history)" | 배포 후 500 스택 39건 모두 `DistributionController.redistribute(DistributionController.java:40)`(`app.log:1294` 외, `grep -c` 39). 배포 전 6건은 `:38`(`app.log:214` 외, `grep -c` 6) | H1 지지 |
| C2 | 이력 조회 쿼리 | `DistributionService.java:36-41` `listByClass` → `DistributionRepository.java:13-14` `@EntityGraph(attributePaths = {"assignment", "classRoom"})` `findByClassRoomIdOrderByDistributedAtAsc`. 학급의 **모든** 배포 행과 과제를 한 번에 로드한다 | 스택 `findByClassRoomIdOrderByDistributedAtAsc` → `listByClass(DistributionService.java:38)`(`app.log:1285-1286`) | H1 지지. 학급 안에 고아 행이 하나라도 있으면 그 학급의 모든 재배포가 실패하는 구조 |
| C3 | 연관 매핑 | `Distribution.java:23-25` `@ManyToOne(fetch = LAZY, optional = false)` `assignment_id`. 대상 과제 행이 없을 때 Hibernate 가 결과 행을 만들다 `FetchNotFoundException` 을 던진다 | `Caused by: org.hibernate.FetchNotFoundException … identifier value 9` → `EntityInitializerImpl.resolveInstance`(`app.log:1304-1306`) | H1 · H2 지지 |
| C4 | 배포 전 실패 경로 | `DistributionService.java:64-66` `loadOrThrow` → `DistributionRepository.java:10-11` `findWithDetailsById` 도 같은 `@EntityGraph`. 고아 행을 **직접** 열 때만 실패 | 배포 전 7건 스택 `findWithDetailsById` → `loadOrThrow(DistributionService.java:65)`(`app.log:204-205`) | H2 지지. v1.4.2 이전에는 배포 41 하나만 실패했던 이유 |
| C5 | 500 으로 바뀐 이유 | `loadOrThrow` 는 배포 행이 없을 때만 `NotFoundException`(404)을 던진다(`DistributionService.java:66`). 참조 과제가 없을 때의 `JpaObjectRetrievalFailureException` 은 전용 핸들러가 없어 `GlobalExceptionHandler.java:59-63` 일반 핸들러로 가서 500 | 로그 문구 `unhandled exception on …`(`app.log:1275`)이 `GlobalExceptionHandler.java:61` 의 메시지와 같다 | 500 의 경로 확인 |
| C6 | 트랜잭션 경계 | `redistribute` 는 자체 `@Transactional`(`DistributionService.java:49`)이고 `:55` 에서 `markRedistributed` 로 쓴 뒤 DTO 를 반환한다. `listByClass` 는 별도 `@Transactional(readOnly = true)`(`:36`). `application.yml:22` `open-in-view: false` 라 영속성 컨텍스트도 공유하지 않는다. 컨트롤러 `:38` → `:40` 사이에 첫 트랜잭션은 이미 커밋된다 | 실패한 요청마다 `redistributed distribution 42|43 …` INFO 가 ERROR 보다 먼저 찍혔다(배포 후 39건 = 500 39건, 예 `app.log:1274` → `:1275`) | 2절의 "커밋 여부 로그로 확인 불가"를 코드로 보완: **코드상 커밋됨**. DB 에서 `distributed_at` 확인은 하지 않음 |
| C7 | 스키마 제약 | 저장소 스키마에는 `fk_distribution_assignment FOREIGN KEY (assignment_id) REFERENCES assignment (id)`(`db/mariadb/init/01-schema.sql:101`)가 있다. 이 제약이 살아 있으면 `assignment_id = 9` 고아 행은 생길 수 없다 | 운영 DB 에 고아 행이 있다는 것은 로그(46건 모두 id 9)로 확인됨 | 운영 스키마가 저장소와 다르거나, FK 검사를 끈 적재 · 과제 삭제가 있었던 것. 어느 쪽인지 **판단 불가** |
| C8 | 배포 전 검증 범위 | `deploy-history.md:17` 스테이징 DB(시드 데이터)에서 재배포 3건 확인. 시드는 과제 1–6, 배포 1–8 뿐이고(`db/mariadb/init/02-seed.sql:119-138`) 배포 41–44 · 과제 9 가 없다. 단위 테스트 `DistributionServiceTest.java:48-75` 는 `redistribute` 3건뿐이고 `listByClass` 테스트와 `DistributionController` 테스트가 없다 | — | 이번 결함은 배포 전 검증으로 잡힐 수 없는 구조였다(재발 방지 대상) |
| C9 | 변경 파일 기록 | `deploy-history.md:7` 은 변경 파일을 `DistributionController.java` 하나로 적었다. `listByClass` 가 v1.4.2 에서 새로 생겼는지, 그 전부터 있던 메서드를 새로 호출한 것인지는 git 이력을 보지 않아 **판단 불가** | — | 판정에는 영향 없음(호출 지점이 새로 생긴 것은 `:16` 과 `:39` 주석으로 확인) |
| C10 | 연결 풀 설정(H5) | 이번 대조에서 풀 설정 값은 보지 않았다. 로그에서 연결 오류가 0건이라 판정에 필요 없음 | `grep -ciE 'Connection is not available\|timeout\|SQLTransient'` 0 | — |

### 3-2. 판정

| # | 판정 | 확신도(메커니즘) | 확신도(계기) | 근거 |
|---|---|---|---|---|
| H1 | **채택 — 직접 원인** | 높음 | 높음 | 로그 반증 3개 모두 불성립(3절 표) + C1 · C2 · C3 · C5. 배포 후 500 39건 = `listByClass` 스택 39건 = 학급 1 재배포 500 39건 |
| H2 | **채택 — 배경 원인** | 높음 | 낮음 | 고아 참조(과제 9)가 배포 전부터 있었다는 것은 높음(C4, 배포 전 7건). 그 행이 배포 41 인지, 학급 1 소속인지, 언제 · 왜 생겼는지(C7)는 DB 를 보지 않아 판단 불가. 다만 학급 1 의 `listByClass` 가 과제 9 에서 실패하므로 "학급 1 안에 과제 9 를 가리키는 행이 하나 이상 있다"는 높음 |
| H3 | **기각(500 원인으로서)** · 별개의 일시 영향 | 높음 | 높음 | 502 8건은 13:41:51–13:41:59 재기동 구간에만 있고(`nginx-access.log:16865-16872`) 설명은 맞다. 13:46:38 이후 500 39건과는 무관 |
| H4 | **기각(원인)** · **악화 요인** | 높음 | 낮음 | 요청량 평탄, 첫 요청부터 500(3절 표). 재시도는 원인이 아니지만, C6 때문에 실패한 재시도마다 재배포 저장이 반복 커밋됐을 가능성이 높다(코드상). 실제 반영 여부는 DB 미확인 |
| H5 | **기각** | 높음 | 높음 | 연결 오류 0건, 예외 46건 모두 엔티티 없음 |

---

## 4. 원인

- **직접 원인**: v1.4.2 가 재배포 응답에 학급 배포 이력을 붙이려고 `DistributionController.java:40` 에서 `listByClass(classId)` 를 호출하게 됐다. 이 조회는 학급의 모든 배포 행과 과제를 `@EntityGraph` 로 함께 읽는다(`DistributionRepository.java:13-14`). 학급 1 에는 없는 과제(id 9)를 가리키는 배포 행이 있어서, 학급 1 의 어느 배포를 재배포해도 이 조회에서 `FetchNotFoundException` 이 났다. 예외 핸들러가 이것을 500 으로 바꿨다(`GlobalExceptionHandler.java:59-63`).
- **배경 원인**:
  - 운영 DB 에 고아 참조 행이 배포 전부터 있었다(09:52:17 첫 오류, `app.log:194`). 저장소 스키마의 FK(`db/mariadb/init/01-schema.sql:101`)로는 생길 수 없는 데이터라서, 운영 스키마나 적재 경로가 저장소와 다른 것으로 보인다(판단 불가).
  - 배포 전 검증이 이 데이터를 만날 수 없었다. 스테이징은 시드 데이터만 썼고(`deploy-history.md:17`, `02-seed.sql:119-138`), `listByClass` 와 새 응답 형식에 대한 테스트가 없다(`DistributionServiceTest.java:48-75`).
  - 배포 전 7건의 500(배포 41)이 경보나 조사 없이 지나갔다. 같은 예외였으므로 이때 고아 행을 찾았으면 이번 장애를 막을 수 있었다.
- **악화 요인**:
  - 쓰기(`redistribute`, `DistributionService.java:49-62`)와 이력 읽기(`listByClass`, `:36-41`)가 서로 다른 트랜잭션이라, 500 을 받은 요청도 재배포 저장은 커밋됐다(코드상, DB 미확인). 사용자는 실패로 보고 다시 눌렀고(교사단말A, 2–4회씩 13묶음 + 단건 1회), 배포 42 · 43 의 배포 시각이 그때마다 바뀌었을 수 있다.
  - "부가 기능(이력)" 하나가 실패하면 "본 기능(재배포)" 응답 전체가 실패하는 구조였다(`DistributionController.java:38-41`).
  - 배포 방식이 단일 호스트 재기동(`deploy-history.md:18`)이라 재기동 9초 동안 502 8건이 났다. 500 과는 별개의 영향이다.
  - 로그 끝까지 롤백 기록이 없다. 롤백 계획(`deploy-history.md:22`, 약 3분)이 있었는데도 약 53분 동안 500 이 이어졌다. 롤백을 했는지는 로그로 확인 불가.

## 5. 근거 로그 줄

| 근거 | 출처 | 말해 주는 것 |
|---|---|---|
| 배포 전 첫 오류 | `app.log:194`, `:204-205`, `:214`, `:224` / `nginx-access.log:3150` | 같은 예외(과제 9 없음)가 배포 전부터 있었고, 그때 경로는 `findWithDetailsById` → `loadOrThrow` → `Controller.java:38` |
| 배포 전 41 단건 조회 실패 | `app.log:451`, `:462` / `nginx-access.log:5563` | 고아 행을 직접 열면 조회 API 도 실패 |
| 기동 버전 | `app.log:1245`, `:1264` | 13:42:00 부터 v1.4.2 가 응답 |
| 신버전 응답 형식 첫 확인 | `nginx-access.log:16884` (13:42:10, 배포 7, 802바이트) | 이력 포함 응답이 실제로 나갔다(배포 전 재배포 응답은 170–200바이트대) |
| 배포 후 첫 500 과 스택 | `app.log:1274-1275`, `:1285-1286`, `:1294`, `:1304-1306` / `nginx-access.log:17155` | 재배포 INFO 가 먼저 찍히고, `listByClass` → `Controller.java:40` 에서 `FetchNotFoundException` |
| 배포 후 500 전체 | `app.log:1275-2887` (ERROR 39) / `nginx-access.log:17155-20084` (500 39) | 배포 42 → 19건, 43 → 20건. 모두 같은 클라이언트(1곳) |
| 반례(다른 학급 성공) | `app.log:1266`, `:1393`, `:1754`, `:2420`, `:2542` / `nginx-access.log:16884`, `:17734` | 학급 2 · 3 재배포는 신버전에서도 200 |
| 502 구간 | `nginx-access.log:16865-16872` / `app.log:1240-1264` | 재기동 중 일시 502 8건만, 그 밖은 0건 |
| 연결 문제 없음 | `app.log` 전체 `grep -ciE` 0건 | H5 기각 |

## 6. 수정안(제안만 — 코드는 고치지 않았다)

1. **즉시**: v1.4.1 로 롤백(`deploy-history.md:22`)하거나, 운영 DB 의 고아 행을 확인 · 정리한다. 정리는 데이터 소유자 판단이 필요하므로 먼저 9절의 조회로 대상을 확정한다. 롤백해도 배포 41 의 오류(배포 전과 같은 7건 유형)는 남는다.
2. **쓰기와 이력 읽기를 한 서비스 메서드로 묶는다**: `DistributionService` 에 "재배포 + 같은 학급 이력" 을 한 `@Transactional` 메서드로 두고, 컨트롤러는 그 메서드 하나만 호출한다(`DistributionController.java:38-40`). 그러면 이력 조회가 실패할 때 재배포 저장도 함께 롤백되어 "500 인데 저장됨" 상태가 없어진다.
3. **부가 정보 실패가 본 기능을 막지 않게 한다(선택)**: 이력 조회 실패 시 재배포 결과는 돌려주고 이력은 비우거나 "이력 조회 실패" 표시를 두는 방안. 2번과 함께 쓸지 하나만 쓸지는 화면 요구(`deploy-history.md:14`)와 맞춰 정한다.
4. **참조 과제 없음의 응답 코드**: `JpaObjectRetrievalFailureException`(또는 `EntityNotFoundException`)을 `GlobalExceptionHandler` 에서 별도로 다뤄 로그에 "데이터 무결성 오류"로 구분해 남긴다. 상태 코드를 500 으로 둘지 다른 값으로 둘지는 팀 결정 사항이다(사용자 잘못이 아니므로 4xx 로 바꾸는 것은 권하지 않음).
5. **테스트 추가**: `listByClass` 서비스 테스트(Mockito), 재배포 응답 형식 `@WebMvcTest`, 고아 참조 행이 있는 학급의 `@DataJpaTest`(H2) — 이번 실패를 재현하는 케이스.
6. **부가 발견(이번 장애와 별개)**: `DistributionService.java:56-60` 이 재배포 사유(`reason`)를 그대로 로그에 남겨 학생 식별자가 운영 로그에 기록된다(예: `app.log:1238`). CLAUDE.md 의 "로그 메시지에 학생 식별자를 넣지 않는다" 규칙과 어긋나므로 사유는 로그에서 빼거나 길이 · 유무만 남기는 것을 제안한다. 또 `deploy-history.md:20` 에 배포 API 인증 토큰이 원문으로 남아 있다 — 토큰 교체와 파이프라인 로그 마스킹이 필요하다.

## 7. 재발 방지

- **배포 전 데이터 무결성 점검**: 응답에 새로 붙는 조회(이번에는 학급 단위 전체 행)가 운영 데이터 전체를 훑게 되는 변경이면, 배포 전에 운영(또는 운영 복제) DB 에서 해당 조회의 고아 참조 수를 확인한다. 예: `SELECT COUNT(*) FROM distribution d LEFT JOIN assignment a ON a.id = d.assignment_id WHERE a.id IS NULL` (로컬 compose DB 의 `readonly` 계정으로 연습 가능).
- **운영 스키마와 저장소 스키마 대조**: `fk_distribution_assignment`(`db/mariadb/init/01-schema.sql:101`)가 운영에도 있는지 확인하고, 없으면 고아 행 정리 후 추가를 검토한다(스키마 변경은 사람 승인 필요).
- **배포 검증 데이터**: 스테이징 시드(`02-seed.sql`)만으로는 운영의 이상 데이터를 만날 수 없다. 고아 참조 · 마감 과제 · 큰 학급 등 "경계 데이터" 픽스처를 테스트에 넣는다.
- **테스트 규칙 이행**: CLAUDE.md 의 "새 public 서비스 메서드 · 새 엔드포인트마다 테스트 1개 이상"을 배포 승인 체크리스트에 넣는다. 이번 변경은 응답 형식이 바뀌었는데 컨트롤러 테스트가 없었다.
- **배포 전 기존 오류 확인**: 배포 승인(13:38, `deploy-history.md:21`) 전에 같은 엔드포인트의 당일 5xx 를 보는 단계를 둔다. 이번에는 같은 경로에서 배포 전 500 이 6건(재배포) + 1건(단건 조회) 있었다.
- **배포 시간 · 방식**: "수업 중 반영 요청"(`deploy-history.md:14`)이라도 단일 호스트 재기동(`:18`)은 502 를 낸다. 대기 호스트 `API-2`(`deploy-history.md:3`, 평소 정지)를 쓴 교대 배포나 수업 외 시간 배포를 검토한다.
- **롤백 판단 기준**: "배포 후 30분 안에 변경된 엔드포인트의 5xx ≥ 3건이면 롤백" 같은 기준을 롤백 계획에 숫자로 적는다.

## 8. 모니터링 항목

기준점: 이번 장애의 첫 ERROR = **13:46:38.113** (`app.log:1275`). 로그 전체의 첫 ERROR 는 09:52:17.335(`app.log:194`)다. 아래 "울렸을 시각"은 이번 로그에 임계값을 대입한 값이다.

| 항목 | 임계값 | 이번에 울렸을 시각(KST) | 첫 ERROR 대비 | 출처 |
|---|---|---|---|---|
| 엔티티 참조 없음 예외 (`FetchNotFoundException` / `JpaObjectRetrievalFailureException`) | 1건 이상 즉시 | **09:52:17** | 장애 첫 ERROR 보다 3시간 54분 21초 **앞섬**, 배포 시작(13:40:05)보다 3시간 47분 48초 앞섬. 로그 전체 첫 ERROR 와 같음 | `app.log:194`, `:224` |
| 같은 경로 5xx | 5분 안에 3건 | 09:52:41(배포 전), 다시 11:18:02 / 배포 후 **13:47:08** | 배포 후 기준 +30초 | `nginx-access.log:3174`, `:8468`, `:17181` |
| 배포 후 변경 엔드포인트 5xx | 배포 완료 후 60분 안에 1건 이상(`POST /api/distributions/*/redistribute`) | **13:46:38** | 0초(배포 파이프라인 완료 13:42:31 대비 +4분 07초, 앱 기동 완료 13:42:00 대비 +4분 38초) | `nginx-access.log:17155` |
| 서비스 전체 500 | 10분 안에 5건 | **13:50:13** | +3분 35초 | `nginx-access.log:17366` |
| 배포 중 502 | 1건 이상(배포 창 안에서는 30초 넘게 이어질 때만 경보) | 13:41:51 발생. "30초 이상" 조건으로는 울리지 않음(8건, 8초) | 첫 ERROR 보다 4분 47초 앞섬(별개 사건) | `nginx-access.log:16865-16872` |
| 같은 클라이언트의 같은 POST 5xx 재시도 | 1분 안에 3건 | 09:52:41 / 배포 후 13:47:08 | +30초 | `nginx-access.log:3174`, `:17181` |
| 고아 참조 행 수(일 1회 · 배포 전) | `distribution` 중 과제 없는 행 > 0 | 로그로 시각 특정 불가. 행이 09:52:17 이전부터 있었으므로 최소 그 전에 울렸을 것 | 장애보다 앞섬(정확한 값 불명) | DB 조회 필요(9절) |

- 가장 일찍, 가장 확실하게 울렸을 항목은 "엔티티 참조 없음 예외 1건 이상"이다. 이 경보가 09:52:17 에 울려 고아 행을 확인했다면 v1.4.2 배포 전에 원인 데이터를 알 수 있었다.
- 5xx 비율 기반 경보(예: 전체 요청 대비 1%)는 이번에 맞지 않는다. 10분 버킷당 요청 500–700건 중 5xx 6–11건(약 1–2%)으로, 경계선에 걸쳐 놓치기 쉽다. 경로별 절대 건수가 낫다.

## 9. 확인하지 못한 것

| 확인하지 못한 것 | 왜 못 했나 | 무엇을 남겨야(조회해야) 알 수 있었나 |
|---|---|---|
| 과제 9 를 가리키는 배포 행이 배포 41 인지, 학급 1 소속인지, 몇 개인지 | 운영 DB 조회 안 함. 로그에 `class_id` 가 실패 요청에는 찍히지만 고아 행 자체의 id 는 안 찍힘 | `SELECT id, class_id, assignment_id FROM distribution WHERE assignment_id NOT IN (SELECT id FROM assignment)`. 또는 예외 로그에 실패한 배포 행 id 를 남기기 |
| 고아 행이 언제 · 왜 생겼나(과제 삭제? FK 없는 적재?) | 운영 스키마 · 변경 이력 없음. 저장소 스키마에는 FK 가 있음(`01-schema.sql:101`) | 운영 `SHOW CREATE TABLE distribution`, 과제 삭제 감사 로그, 데이터 적재 작업 기록 |
| 500 을 받은 39건의 재배포 저장이 실제로 커밋됐나 | 코드로는 커밋(C6)이지만 DB 미확인 | 배포 42 · 43 의 `distributed_at` · `redistributed` 값과 마지막 실패 시각(14:28:06 · 14:34:45) 비교. 응답 코드와 함께 트랜잭션 커밋 로그를 남기기 |
| 롤백 · 핫픽스를 했는지, 장애가 언제 끝났는지 | 로그가 14:39:58 에서 끝남. 이후 재기동 기록 없음 | 14:40 이후 `app.log` · 배포 파이프라인 기록 |
| 저장소 코드가 v1.4.2 배포본과 같은지 | git 이력 · 배포 이미지를 보지 않음(이번 범위에서 git 조작 금지) | 배포 이미지의 커밋 해시를 기동 로그에 남기기(`app.log:1245` 에는 버전만 있음) |
| `listByClass` 가 v1.4.2 에서 새로 생겼는지(C9) | 변경 파일 목록에 서비스 파일이 없음(`deploy-history.md:7`), git 미확인 | 배포 이력에 diff 링크 · 커밋 해시 |
| 응답 지연 여부 | nginx 로그에 응답 시간 필드 없음(1-1) | nginx `log_format` 에 `$request_time` · `$upstream_response_time` 추가 |
| 09:52 에 누가 41 을 재배포하려 했고 오류를 어떻게 처리했나 | 사용자 · 문의 기록 없음 | 교사 문의 · 헬프데스크 기록 |
| 배포 파이프라인 완료 시각(13:42:31)과 앱 기동 완료(13:42:00) 사이 31초의 의미 | 파이프라인 로그 없음 | 헬스 체크 · 트래픽 전환 단계 로그 |

---

마스킹 적용: IP 5건 · 이메일 2건 · 학생 식별자 6건 · 토큰 1건 · 비밀번호 0건
