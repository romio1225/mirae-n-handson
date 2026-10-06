# 외주 PR 1 검증 — Opus 리뷰

- 대상: `vendor-prs/pr-1-missing-tests.patch` (적용하지 않고 내용만 읽음. `git apply --stat` · `--check` 만 실행, `--check` 통과)
- 요청 문장: "단원별 문항 수 통계 API 추가" (patch 머리말 `Subject:` 줄 `vendor-prs/pr-1-missing-tests.patch:4` 과 사용자 입력이 일치)
- 리뷰 모델: Opus (새 세션, 다른 리뷰 판정 미열람)
- 체크리스트: `templates/approval-checklist.md` — **승인 체크리스트 v1 · 2회차 (2026-10-06)**, 팀 규칙 `CLAUDE.md`
- 줄번호 계산: patch 적용 후 파일 기준. 각 hunk 헤더 `@@ +start` 의 start 를 첫 줄로 두고, 문맥(` `) · 추가(`+`) 줄마다 1씩 더하며 삭제(`-`) 줄은 세지 않았다. 새 파일은 `@@ +1,9` 부터 센다.
- 변경 파일(`git apply --stat`): `modern/api/.../item/UnitController.java` (+6), `.../item/UnitItemCountResponse.java` (신규 +9), `.../item/UnitService.java` (+16 −1)
- 테스트: 사용자 지시로 실행하지 않음

판정: 반려(확인 필요)

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 확인 필요 | 사용자 지시로 `./gradlew test` 를 실행하지 않음(통과 · 실패 수 없음). PR 설명의 "기존 테스트 전부 통과 확인"(`vendor-prs/pr-1-missing-tests.patch:14`)은 리뷰어가 재현하지 않은 주장. 기존 테스트 삭제 · `@Disabled` 없음(patch 에 `src/test` 변경 0건). 새 메서드 · 엔드포인트 테스트 누락은 [경고] 이슈 #1 · #2 |
| 치명 이슈 0건 | 충족 | 치명 이슈 없음. 비밀값 리터럴 · SQL 이어 붙이기 · DROP/TRUNCATE · 인증 우회 · `legacy/` · `characterization/` 변경 · 로그 개인정보(로그 인자는 건수 `counts.size()` 뿐, `UnitService.java:41`) · `.env`/운영 DB 없음 |
| 요청 범위 이탈 없음 | 충족 | 변경 파일 3개 모두 `com.example.item` 의 단원 통계 엔드포인트 · DTO · 서비스로 요청 문장으로 설명됨. 무관한 줄 변경 없음(삭제 1줄은 생성자 시그니처 교체 `UnitService.java:18`). `build.gradle` · `package.json` · `db/` · `application.yml` 변경 없음 |
| 컨벤션 준수 | 충족 | [반려] 항목 위반 없음: 컨트롤러 생성자는 `UnitService` 만 받음(`UnitController.java:15`), 반환 타입은 record `List<UnitItemCountResponse>`(`UnitController.java:27`, `UnitItemCountResponse.java:4`), `ItemRepository` 는 같은 도메인(`com.example.item`), 클래스 레벨 `@Transactional(readOnly = true)` 유지(`UnitService.java:10`), 공개 판단은 `ItemStatus.ACTIVE`(`UnitService.java:39`), `@Autowired` · `now()` · `System.out` · 빈 catch · 와일드카드 import 없음, 경로 `/api/units/item-counts` 는 `/api/<복수형>` 규칙 충족. [경고] 네이밍 · 매직 넘버 위반 없음 |

이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | modern/api/src/main/java/com/example/item/UnitService.java:36-43 | 새 public 서비스 메서드 `countActiveItemsByUnit()` 를 호출하는 테스트가 없다. patch 에 `src/test` 변경이 0건이고, 기존 `UnitServiceTest` 는 `listUnits` 만 검증하며 `ItemRepository` mock 이 없다(`UnitServiceTest.java:19-23`). 체크리스트 1장 [경고] · `CLAUDE.md` "새 public 서비스 메서드마다 테스트가 1개 이상" 위반 | `UnitServiceTest` 에 `@Mock ItemRepository` 를 추가하고, 단원 순서 유지 · 문항 0개 단원 포함 · `ItemStatus.ACTIVE` 로 센다는 것을 검증하는 Mockito 테스트(camelCase 이름 + 한국어 `@DisplayName`, 픽스처 `ItemFixtures.unit`)를 추가 |
| 2 | 경고 | modern/api/src/main/java/com/example/item/UnitController.java:26-29 | 새 엔드포인트 `GET /api/units/item-counts` 를 호출하는 `@WebMvcTest` 가 없다(`UnitControllerTest` 파일 자체가 없음, `WebConfigTest` 는 CORS 만 확인). 체크리스트 1장 [경고] · `CLAUDE.md` "새 엔드포인트마다 테스트가 1개 이상, 컨트롤러는 `@WebMvcTest`" 위반 | `@WebMvcTest(UnitController.class)` + `@MockBean UnitService` 로 200 응답과 JSON 필드명(`code`, `name`, `grade`, `activeItemCount`)을 검증하는 테스트 추가 |
| 3 | 제안 | modern/api/src/main/java/com/example/item/UnitService.java:37-40 | 단원 목록 스트림 안에서 단원마다 `itemRepository.countByUnitIdAndStatus(...)` 를 호출해 단원 수 N 만큼 COUNT 쿼리가 나간다(N+1, convention-check `review-points.md` R5 신호). 현재 단원 수가 적으면 문제는 아니나 하나의 readOnly 트랜잭션이 커넥션을 N 회 쿼리 동안 쥔다(Hikari 최대 5) | `group by unit` 집계 쿼리 1회(예: JPQL `select i.unit.id, count(i) ... where i.status = :status group by i.unit.id`)로 맵을 만들고 단원 목록과 합쳐 0 을 채우는 방식 검토. 채택 여부는 만든 쪽이 결정 |

확인 필요

| 파일 또는 범위 | 확인할 질문 |
|---|---|
| `modern/api` 전체(`./gradlew test`) | patch 를 `review/pr-1` 브랜치에 적용해 `./gradlew test` 를 실행했을 때 실패 · 오류 수가 0 입니까? (사용자 지시로 이번 리뷰에서는 실행하지 않음. 생성자 인자가 늘었으나 `UnitServiceTest` 는 `@InjectMocks` 생성자 주입이라 빠진 `ItemRepository` 자리에 null 이 들어가 기존 두 테스트는 그대로 통과할 것으로 보이며, 이는 실행으로 확인해야 함) |
| `modern/api/src/main/java/com/example/item/UnitService.java:39` | (R5) 단원 수만큼 COUNT 쿼리가 나가도 됩니까, 집계 쿼리 1회로 바꾸도록 요구합니까? |
| `modern/api/src/main/java/com/example/item/UnitItemCountResponse.java:4` | 기존 `UnitResponse` 에는 `id` 가 있는데(`UnitResponse.java:4`) 통계 응답에는 `id` 가 없습니다. 화면에서 단원 목록과 `code` 로만 결합하는 것이 의도입니까? |
| 이슈 표 전체 | 체크리스트 0장: 이슈 표의 `파일:줄번호` 를 사람이 patch 적용 후 파일과 대조해 일치를 확인했습니까? (리뷰어는 hunk 헤더로 계산함) |
