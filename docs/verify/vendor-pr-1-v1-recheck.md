# pr-1-missing-tests v1 체크리스트 재판정

- 대상: `vendor-prs/pr-1-missing-tests.patch` (patch 미적용, 내용만 읽음. 테스트 미실행)
- 요청 문장(patch 머리말 `Subject:`, patch:4): "요청 범위: 단원별 문항 수 통계 API 추가"
- 기준 파일 버전 줄(`templates/approval-checklist.md:1-3`, 디스크 현재본):
  - "# 승인 체크리스트 v1"
  - "v1 · 2회차 (2026-10-06, 에듀테크R&D팀 초안)"
- 줄번호 표기: `파일:줄번호` 는 patch 적용 후 기준(patch 의 `@@` 헝크로 계산). `patch:N` 은 patch 파일의 줄이다.

판정: 반려(확인 필요)

> 체크리스트 판정 형식(`approval-checklist.md:71`): "기준 안에 "확인 필요"가 남아 있으면 그 기준은 "충족"으로 쓰지 않고 "확인 필요"로 쓴다. 이 경우 판정은 `반려(확인 필요)` 이며, 사람이 답하면 다시 판정한다."
> 테스트 기준에 확인 필요가 1건 남아 있어(아래 확인 필요 1) 이 규칙으로 판정이 나왔다. [반려] 항목에 "아니오"는 없다. 확인 필요 1이 해소되면 판정은 `승인(경고 2)` 가 된다.

## 기준별 결과

| 기준 | 결과 | 근거(인용 항목 · 표시) |
|---|---|---|
| 테스트 통과 | 확인 필요 | 1장 [반려] "바뀐 폴더의 검증 명령을 실제로 실행했다 — `modern/api`: `./gradlew test` / ..." : 사용자가 알려 준 결과는 `./gradlew test` 39 통과 / 0 실패인데, 지정된 `docs/verify/vendor-pr-verdicts.md` 의 pr-1 행(5줄)과 아래 v1 표(13줄)에 이 숫자가 없다. 이 숫자는 `migration-review-1.md:12` · `migration-review-2.md:12`(grade 이관 검증)에만 있다. 같은 파일 pr-3 행(7줄)에만 "41개 통과"가 있다. 따라서 patch 를 적용한 상태의 실행 결과인지 확인되지 않는다. 같은 장 [반려] "실행한 명령의 실패 · 오류 수가 0이다" : 39/0 이 patch 적용본의 결과라면 예. 같은 장 [반려] "기존 테스트를 지우거나 `@Disabled` · `.skip` · `.only` 로 바꾼 줄이 없다." : 예(patch 는 `src/main` 3개 파일만 건드림, patch:16-20). 같은 장 [경고] "새 public 서비스 메서드 · 새 엔드포인트 · `src/components/` 의 새 컴포넌트마다 그것을 호출하는 테스트가 1개 이상 있다(...)" : 아니오 → 경고 #1, #2 |
| 치명 이슈 0건 | 충족 | 2장 [반려] 8개 항목(비밀값 리터럴 / SQL 문자열 이어 붙이기 / DROP · TRUNCATE / 인증 · 권한 / `legacy/` 변경 / `characterization/` 수정 / 로그에 STU · 이메일 · 토큰 / 운영 DB · `.env`) 모두 예. 변경 3개 파일에 해당 줄이 없다. 로그는 `log.debug("counted active items for {} units", counts.size())`(UnitService.java:38 근방, patch:83)로 개수만 남긴다 |
| 요청 범위 이탈 없음 | 충족 | 3장 [반려] "변경 파일 목록의 모든 파일이 요청 문장으로 설명된다(...)" : 예. 변경 파일 3개(`UnitController.java` · `UnitItemCountResponse.java` · `UnitService.java`)가 모두 `com.example.item` 의 단원별 문항 수 통계다. 같은 장 [반려] "요청 파일 안에서도 요청과 무관한 줄 변경이 없다(...)" : 예(추가 + 생성자 파라미터 1개뿐, patch:56-66). 같은 장 [반려] "`build.gradle` 의 `dependencies` · `package.json` · ... 이 바뀌었다면 ..." : 해당 파일 변경 없음 → 예 |
| 컨벤션 준수 | 충족 | 4장 [반려] 8개 항목 모두 예: "컨트롤러 생성자가 `*Service` 타입만 받고, ..." (컨트롤러는 `unitService` 호출만, patch:32-35) / "서비스가 다른 도메인의 `*Repository` 를 주입받지 않는다." (`ItemRepository` 는 같은 `com.example.item` 도메인, patch:60-63) / "컨트롤러 반환 타입이 record 또는 그 `List` 다(...)" (`List<UnitItemCountResponse>`, record, patch:33,46) / 빈 catch · System.out · @Autowired · 인자 없는 now() · GlobalExceptionHandler 밖 변환 · fetch 위반 없음. `UnitService` 클래스에 `@Transactional(readOnly = true)` 가 있어(UnitService.java:10) 새 조회 메서드도 CLAUDE.md 의 readOnly 규칙을 만족하고, `ItemStatus.ACTIVE`(문자열 `"A"`)를 쓴다. 4장 [경고] 3개("매직 넘버 ..." · "네이밍 규칙 ..." · "`any` · `@ts-ignore` ...")는 위반 없음. 4장 [경고] "입력 값 범위 검증(예: 난이도 1~5)이 요청된 파라미터에 있고 범위 밖이면 400 을 돌려준다." : 새 엔드포인트에 입력 파라미터가 없어 해당 없음(예) |

0장 판정 전 확인:
- [반려] "요청 문장이 한 문장으로 주어졌다(...)" : 예(patch:4).
- [반려] "검증 범위(git 범위 또는 patch)가 요청 1건에 해당하는 커밋만 담고 있다(...)" : 예(patch 1건, 1개 요청).
- [반려] "이슈 표의 모든 `파일:줄번호` 를 사람이 원본 파일과 대조해 일치를 확인했다(어긋난 행은 고치고 "(사람 판정)" 표시)." : 확인 필요 — 사람 대조 전이다. 아래 줄번호는 patch 헝크로 계산했고 `vendor-pr-verdicts.md:5` 의 `UnitService.java:36` · `UnitController.java:26-29` 와는 일치하지만, 사람이 확인하기 전까지 이 항목을 예로 쓰지 않는다.

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거(인용 항목 · 표시) | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | modern/api/src/main/java/com/example/item/UnitService.java:36 | 1장 [경고] "새 public 서비스 메서드 · 새 엔드포인트 · `src/components/` 의 새 컴포넌트마다 그것을 호출하는 테스트가 1개 이상 있다(...)" 에 "아니오". 새 public 메서드 `countActiveItemsByUnit()` 를 호출하는 테스트가 patch 에 없다(patch 는 `src/test` 변경 0건, patch:16-20) | `UnitServiceTest` 에 Mockito 테스트 추가(정렬 유지, 문항 0개 단원 0 포함, ACTIVE 만 집계), 한국어 `@DisplayName` |
| 2 | 경고 | modern/api/src/main/java/com/example/item/UnitController.java:26-29 | 같은 [경고] 항목 "아니오". 새 엔드포인트 `GET /api/units/item-counts` 를 호출하는 테스트가 없다 | `@WebMvcTest` 로 200 과 JSON 필드(`code` · `name` · `grade` · `activeItemCount`) 검증 |
| 3 | 제안 | modern/api/src/main/java/com/example/item/UnitService.java:38-39 | 체크리스트 항목에 걸리지 않는 개선 의견(심각도 정의 "제안 — 체크리스트 항목에 걸리지 않는 개선 의견. 목록으로만 남긴다."). 단원마다 `countByUnitIdAndStatus` 를 불러 단원 수만큼 쿼리가 나간다 | 단일 GROUP BY 쿼리로 바꾸고 `@DataJpaTest` 로 검증 |

심각도 정의 인용(`approval-checklist.md:16`): "경고 — [경고] 항목에 걸리는 문제. 판정에 영향 없음. ..." 이슈 #1 · #2 는 판정에 영향이 없다.

## 확인 필요

| 파일 또는 범위 | 확인할 질문 |
|---|---|
| 테스트 기준 (1장 [반려] "바뀐 폴더의 검증 명령을 실제로 실행했다 ...") | 사용자가 전달한 `./gradlew test` 39 통과 / 0 실패가 `vendor-pr-verdicts.md` 에는 없다. 이 숫자가 patch 를 `review/pr-1` 같은 브랜치에 적용한 상태에서 나온 것인지, 아니면 patch 없는 기준 상태(migration-review 의 39/0)인지 답해 달라. patch 적용본의 결과가 맞으면 판정은 `승인(경고 2)` 로 바뀐다 |
| 이슈 표 줄번호 (0장 [반려] "이슈 표의 모든 `파일:줄번호` 를 사람이 원본 파일과 대조해 일치를 확인했다(...)") | 사람이 `review/pr-1` 의 원본 파일과 UnitService.java:36, UnitController.java:26-29 를 대조해 달라 |

## 참고: `vendor-pr-verdicts.md` 와의 차이

- 그 파일 v1 표(13줄)의 "승인(경고 2)" 와 경고 2건은 이 재판정과 같다. 이 재판정은 그 결론을 따라가지 않고 체크리스트로 직접 판정했으며, 차이는 테스트 결과의 출처가 기록에 없다는 점(위 확인 필요 1)뿐이다.
- 같은 파일 5줄의 v0 반려 사유 "테스트 통과(테스트 충분성)"는 v1 에서 [경고] 로 내려간 항목이다.
