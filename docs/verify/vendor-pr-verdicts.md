# 외주사 PR 판정 기록

| PR | 판정 | 미충족 기준 | 근거(파일:줄번호) | 외주사에 돌려보낼 수정 요청 |
|---|---|---|---|---|
| pr-1-missing-tests (단원별 문항 수 통계 API 추가) | 반려 | 테스트 통과(테스트 충분성) — 새 public 서비스 메서드 · 새 엔드포인트에 테스트 없음 | modern/api/src/main/java/com/example/item/UnitService.java:36 (`countActiveItemsByUnit` 테스트 없음), modern/api/src/main/java/com/example/item/UnitController.java:26-29 (`GET /api/units/item-counts` 테스트 없음), modern/api/src/test/java/com/example/item/ (이 PR 에서 변경 없음, `item-counts`·`countActiveItemsByUnit`·`UnitItemCountResponse` 참조 0건) | (1) `UnitServiceTest` 에 `countActiveItemsByUnit` Mockito 테스트 추가 — 정렬 유지, 문항 없는 단원 0 포함, ACTIVE 만 집계를 검증하고 한국어 `@DisplayName` 을 단다. (2) `@WebMvcTest` 로 `GET /api/units/item-counts` 테스트 추가 — 200 과 JSON 필드(`code`·`name`·`grade`·`activeItemCount`) 검증. (3) 가능하면 단원마다 count 를 부르는 N+1(UnitService.java:38-39)을 단일 GROUP BY 쿼리로 바꾸고 `@DataJpaTest` 로 검증. |
| pr-2-hardcoded-secret (외부 채점 서버 연동 클라이언트 추가) | 반려 | 치명 이슈 0건 — 채점 서버 계정(아이디·비밀번호)이 소스에 리터럴로 들어 있음. 부가: 테스트 충분성(`requestGrading` 무테스트), 컨벤션(문자열 이어 붙인 JSON 본문) | modern/api/src/main/java/com/example/assignment/GradingServerClient.java:28-29 (`username`·`password` 필드에 리터럴, 값은 옮겨 적지 않음), :60, :65-67 (그 값으로 Basic 인증 헤더 생성), :56 (studentId 를 JSON 문자열에 직접 이어 붙임, 이스케이프 없음), :38 (`requestGrading` 테스트 없음; modern/api/src/test/java/com/example/assignment/GradingServerClientTest.java:13-16 은 `buildRequest` 만 검증) — 줄번호는 사람이 review 브랜치 원본으로 바로잡음(리뷰 세션 인용 :34-35 · :72-73 · :62 · :44-59 · 테스트 :96-103 이 실제와 어긋남) (사람 판정) | (1) 하드코딩된 계정 · 비밀번호를 제거하고 `@Value("${grading.username}")` · `${grading.password}` 같은 환경 변수 주입으로 바꾼다(기본값 없이, 키 파일 · 설정에 리터럴 금지). 이미 저장소 이력에 올라간 비밀번호는 노출된 것으로 보고 채점 서버에서 폐기 · 재발급한다. (2) `requestGrading` 의 정상(200) · 비200 · IOException 경로 테스트를 추가한다(HttpClient 주입 또는 로컬 목 서버). (3) 요청 본문을 문자열 연결 대신 `ObjectMapper` 로 직렬화한다. |
| pr-3-out-of-scope (문항 검색에 난이도 필터 추가) | 반려 | 요청 범위 이탈 없음 — 난이도 필터와 무관한 assignment 도메인 2개 파일을 리팩토링 · 로그 변경. 부가: 테스트 통과(테스트 충분성) — 새 엔드포인트 테스트 없음, 컨벤션(난이도 범위 검증 없음) | modern/api/src/main/java/com/example/assignment/DistributionService.java:52,62-66 (재배포 마감 검사를 `assertNotClosed` 로 추출), modern/api/src/main/java/com/example/assignment/ReportService.java:65,82-88 (평균 계산을 `averageOf` 로 추출), :71-72 (로그에 `average` 값 추가 — 동작 변경), modern/api/src/main/java/com/example/item/UnitController.java:31-34 (`GET /api/units/{code}/items?level=` 컨트롤러 테스트 없음; modern/api/src/test/java/com/example/item/ 에 `@WebMvcTest` 추가 없음), modern/api/src/main/java/com/example/item/UnitService.java:39-48 (`level` 1~5 검증 없음 — 범위 밖 값은 400 대신 빈 목록; `MAX_LEVEL` 상수 없음). 테스트: `cd modern/api && ./gradlew test` 41개 통과 · 실패 0 · 건너뜀 0 | (1) `DistributionService`·`ReportService` 변경을 이 PR 에서 되돌리고 필요하면 별도 PR 로 올린다(로그 `average` 추가 포함). (2) `UnitController.listItemsByLevel` 에 `@WebMvcTest` 추가 — `level` 있을 때 200 · JSON 필드, 없는 단원 404, `level` 없는 요청은 기존 `ItemController` 로 가는지 검증. (3) `level` 이 1~5 밖이면 400 이 되도록 검증하고(상수 `MIN_LEVEL`·`MAX_LEVEL` 사용) 테스트를 추가한다. (4) 가능하면 `ItemController` 의 기존 `/units/{code}/items` 와 한 곳에 두어 경로를 나누지 않는다. |

## v1 기준(2회차 실습 4)으로 다시 판정하면

| PR | v0 판정 | v1 판정 | 달라진 이유 |
|---|---|---|---|
| pr-1-missing-tests | 반려 | 승인(경고 2) | 새 메서드 · 엔드포인트 테스트 누락이 [경고] 항목으로 내려갔다(체크리스트 1장 마지막 항목) |
| pr-2-hardcoded-secret | 반려 | 반려 | 비밀값 리터럴은 [반려] 항목 그대로(체크리스트 2장 첫 항목). 테스트 누락은 경고로 내려감 |
| pr-3-out-of-scope | 반려 | 반려 | 요청 밖 파일(assignment 2개)은 [반려] 항목 그대로(체크리스트 3장 첫 항목). 테스트 누락 · level 검증은 경고 |

## 교차 리뷰 · 사람 보충 (심화 1 · 3)

- pr-1 테스트 결과(사람 보충): `review/pr-1` 에서 Sonnet 리뷰 세션이 `./gradlew test` 실행 → 39 통과 / 0 실패. 위 pr-1 행에 이 숫자가 빠져 있어 v1 재판정(`vendor-pr-1-v1-recheck.md`)이 테스트 기준을 "확인 필요"로 남겼다. 이 보충으로 v1 판정은 `승인(경고 2)` 로 확정한다. (사람 판정)
- pr-1 Opus 교차 리뷰(`vendor-pr-1-opus.md`): 경고 2건(UnitService.java:36 · UnitController.java:26-29 테스트 없음) · 제안 1건(N+1)이 Sonnet 과 같다. Opus 만 잡은 것: `UnitItemCountResponse.java:4` 에 단원 `id` 가 없는 것이 의도인지(확인 필요) → 외주사 회신에 질문으로 넣는다.
