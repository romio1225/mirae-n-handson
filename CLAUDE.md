# CLAUDE.md

Claude Code 심화 과정 실습 저장소. 더미 에듀테크 도메인(문항 은행 · 과제 배포 · 성적 집계)의 레거시(`legacy/`)를 현행 스택(`modern/`)으로 이관한다.

- 답변과 문서는 한국어로 쓴다.
- `legacy/` 는 분석 · 이관 대상이며 허락 없이 수정하지 않는다.

## 빌드 · 테스트 명령

```bash
cd modern/api && ./gradlew test                                                           # 전체 테스트. H2 라서 DB 없이 통과한다
cd modern/api && ./gradlew test --tests 'com.example.item.ItemServiceTest'                 # 한 클래스
cd modern/api && ./gradlew test --tests 'com.example.item.ItemServiceTest.getItemMapsToResponse'   # 한 메서드
cd modern/api && ./gradlew build                                                          # 테스트 포함 빌드
cd modern/api && ./gradlew bootRun         # :8080. 먼저 저장소 루트에서 docker compose --profile modern up -d

cd modern/web && npm ci                                                   # 의존성 설치. npm install 은 쓰지 않는다
cd modern/web && npm run lint && npm run typecheck && npm test            # 커밋 전 검증 세트
cd modern/web && npx vitest run src/components/ItemTable.test.tsx         # 한 파일
cd modern/web && npm run build                                            # dist/ 생성
cd modern/web && npm run dev                                              # :5173, /api 는 8080 으로 프록시
```

- 작업을 마치면 바꾼 쪽의 검증 명령(api: `./gradlew test`, web: lint · typecheck · test 세 개)을 실행하고 통과 · 실패 수를 답변에 적는다. 실행하지 않았으면 "실행하지 않음"이라고 적는다.
- 검증 명령이 하나라도 실패하면 답변에 "완료"라고 쓰지 않는다.

## 코딩 컨벤션

### modern/api (Spring Boot 3.3 · Java 21)

- 패키지는 도메인 단위(`com.example.item`, `com.example.assignment`)이고 한 도메인 안에 Controller → Service → Repository → 엔티티가 있다.
- 컨트롤러 생성자의 파라미터는 `*Service` 타입만 받는다. 컨트롤러에 `*Repository` 주입, SQL 문자열, `try`/`catch` 가 없다.
- 서비스는 다른 도메인의 `*Repository` 를 주입받지 않는다. 다른 도메인 데이터는 그 도메인의 `*Service` 로 읽는다.
- 컨트롤러 메서드의 반환 타입은 `record` DTO(`*Response`) 또는 그 `List` 다. `@Entity` 클래스를 반환하지 않는다.
- 의존성 주입은 `private final` 필드 + 생성자로 한다. `src/main` 에 `@Autowired` 가 없다.
- 조회만 하는 서비스 메서드는 `@Transactional(readOnly = true)` 가 붙어 있다(클래스 레벨 포함).
  - 예외: MariaDB(JPA)를 쓰지 않고 별도 JDBC 풀만 읽는 서비스(예: `com.example.grade` 의 MS-SQL `GradesJdbc`)는 붙이지 않는다. 트랜잭션 관리자가 쓰지 않는 MariaDB 연결(itembank-pool, 최대 5)을 잡기 때문이다. 이때 클래스 주석에 사유를 적는다.
- `@Transactional` 메서드 안에서 외부 HTTP 호출이나 DB 와 무관한 반복 계산(예: `ReportService.sign`)을 하지 않는다. 그런 작업은 트랜잭션이 없는 메서드로 옮긴다.
- 예외 → HTTP 변환은 `common/GlobalExceptionHandler` 에만 있다. 없는 리소스는 `NotFoundException`(404), 검증 실패는 400 으로 매핑된다.
- 빈 `catch` 블록이 없다. `catch` 는 로그를 남기고 다시 던지거나 다른 예외로 감싸 던진다.
- 로그는 SLF4J `Logger` 로만 남긴다. `System.out` · `System.err` · `printStackTrace()` 가 없다.
- 로그 메시지에 학생 식별자(`STU-…`) · 이메일 · 토큰 값을 넣지 않는다.
- 공개 문항은 `ItemStatus` 로 판단한다. 삭제 플래그 컬럼으로 판단하는 코드가 없다.
- `*Service` 클래스는 현재 시각을 주입받은 `Clock` 으로 얻는다. `*Service` 안에 인자 없는 `now()` 호출(`LocalDateTime.now()`, `Instant.now()` 등)이 없다.
- 매직 넘버 대신 이름 붙인 상수를 쓴다(예: 난이도 상한 `MAX_LEVEL = 5`). 와일드카드 import(`.*`)가 없다.
- 새 엔드포인트 경로는 `/api/<도메인 복수형>` 으로 시작한다.

### modern/api 테스트

- 새 public 서비스 메서드 · 새 엔드포인트마다 테스트가 1개 이상 있다.
- 컨트롤러는 `@WebMvcTest`, 서비스는 Mockito 단위 테스트, 리포지토리 쿼리는 `@DataJpaTest`, 스프링 컨텍스트 테스트는 `@ActiveProfiles("test")`(H2) 를 쓴다.
- 테스트 메서드 이름은 camelCase 이고 모든 `@Test` 에 한국어 `@DisplayName` 이 있다. 문항 픽스처는 `ItemFixtures` 를 쓴다.
- 테스트가 실제 MariaDB · 외부 네트워크에 접속하지 않는다.

### modern/web (React 18 · TypeScript · Vite · Vitest)

- `tsconfig.json` 의 `strict` · `noUncheckedIndexedAccess` 등 검사 옵션을 끄지 않는다.
- `any`, `as unknown as`, `@ts-ignore`, `@ts-expect-error` 가 없다. `eslint-disable` 주석은 같은 줄에 사유가 있다.
- 타입만 가져오는 import 는 `import type` 을 쓴다(ESLint `consistent-type-imports`).
- 컴포넌트는 함수 컴포넌트이고 이름 있는 export(`export function X`)로 내보낸다. `export default`, 클래스 컴포넌트, `React.FC`, `defaultProps` 가 없다.
- props 타입은 컴포넌트 파일 안에 `interface <컴포넌트명>Props` 로 선언한다.
- 이벤트 prop 이름은 `on<동작>`, 컴포넌트 내부 핸들러 함수 이름은 `handle<동작>` 이다.
- `fetch` 호출은 `src/api/client.ts` 의 `getJson` 한 곳에만 있다. 엔드포인트별 함수는 도메인별 파일(`src/api/items.ts`, `src/api/grades.ts`), 응답 타입은 `src/api/types.ts` 에 둔다.
- 응답 타입의 필드명은 `modern/api` 의 `*Response` 가 내보내는 JSON 필드명과 같다(`@JsonProperty` 가 있으면 그 이름, 예: `unit_name`).
- 컴포넌트는 서버 데이터를 `src/hooks/` 의 `useApiQuery` 기반 훅으로만 읽는다. 컴포넌트 파일이 `src/api/` 의 함수를 직접 import 하지 않는다.
- 조회 요청은 `AbortSignal` 을 받고, 훅의 cleanup 에서 취소한다.
- 컴포넌트에서 HTTP 상태 코드를 비교하지 않는다. 오류 문구는 `useApiQuery` 의 `toErrorMessage` 가 만든다.
- 조회 화면은 로딩 · 오류 · 빈 결과를 각각 다른 문구로 표시한다.
- `useEffect` 안에서 다른 상태 값으로부터 계산한 값을 `setState` 하지 않는다. 파생 값은 렌더 중에 계산하거나 `useMemo` 로 만든다.
- `console.log` · `dangerouslySetInnerHTML` 이 없다.

### modern/web 테스트

- `src/components/` 의 새 컴포넌트마다 `<이름>.test.tsx` 가 있고, 렌더 확인 1개 + 클릭 · 입력 상호작용 1개 이상을 검증한다.
- 요소 조회는 `getByRole` · `getByLabelText` · `getByText` 를 쓴다. `getByTestId` 를 쓰면 같은 줄 주석에 이유를 적는다.
- 네트워크는 `src/test/mockFetch.ts` 로 가로챈다. 테스트가 실제 서버에 요청을 보내지 않는다. 공용 테스트 데이터는 `src/test/fixtures.ts` 에 둔다.
- 스냅샷 테스트(`toMatchSnapshot`)를 `modern/web` 에 만들지 않는다.

## 완료 기준

- 이관 · 리팩토링 작업은 `characterization/` 의 `npm test` 가 전부 통과하기 전에는 완료라고 보고하지 않는다.
- 테스트가 실패하면 실패한 케이스와 차이를 그대로 보고한다. 요약해서 "거의 됐다"고 말하지 않는다.
- 테스트를 통과시키려고 `characterization/` 의 테스트 코드나 스냅샷 파일을 고치지 않는다. 스냅샷을 바꿔야 한다고 판단되면 멈추고 묻는다.
- 레거시 동작이 버그로 보여도 이관 중에는 고치지 않는다. "의심 동작" 목록으로 따로 보고한다.
- T-SQL 계산을 Java 로 옮길 때 중간 결과의 타입 · 자릿수 · 버림/반올림을 DB 에서 `SELECT` 로 확인한 뒤 옮긴다(예: `DECIMAL ÷ INT` 는 소수 6자리에서 버린다).
- SQL 의 `GROUP BY` · `PARTITION BY` · `JOIN` 을 Java 맵으로 옮길 때 DB 정렬 규칙(대소문자 · 뒤 공백 무시)을 키 비교에 반영한다.

## 금지 사항

- `legacy/` 는 분석 · 이관 대상이며 허락 없이 수정하지 않는다. 레거시 규칙을 옮길 때는 근거를 `파일:줄번호` 로 답변에 적는다.
- 다음 파일은 바꾸기 전에 사람에게 묻고 이유와 대안을 같이 적는다: `build.gradle` 의 `dependencies`, `package.json`, `db/` 의 스키마 · 시드, `application.yml` 의 접속 · 커넥션 풀 설정(Hikari 최대 5 · 대기 3초는 `incident-logs/a-connection-pool` 실습과 연결돼 있다).
- `package-lock.json` 을 손으로 고치거나 지우지 않는다.
- `.env`, `.env.*` 를 읽거나 만들지 않는다. 운영 계정 · 토큰 · 키를 코드 · 설정 파일에 리터럴로 넣지 않는다(실습용 더미 값 `app-pass` 는 예외).
- 운영 DB 호스트에 접속하는 명령 · 설정을 만들지 않는다. DB 조회는 로컬 compose DB 의 읽기 전용 계정(`readonly`)으로 한다.
- 테이블을 `DROP` · `TRUNCATE` 하는 코드를 만들지 않는다.
- `characterization/` 의 테스트 · 스냅샷을 고쳐서 비교를 통과시키지 않는다. 비교가 실패하면 `modern/` 코드를 고친다.
- `vendor-prs/*.patch` 를 적용하지 않는다(리뷰 실습용으로 일부러 결함을 넣은 파일).
- README 의 "이 저장소에 넣지 않은 것" 표에 있는 파일(`.claude/`, `hooks/`, `.mcp.json`, `docs/` 문서 등)을 요청 없이 만들지 않는다.
- 요청 범위 밖의 파일을 고치지 않는다. 포맷팅 · import 정리도 요청 범위 안의 파일에서만 한다.

## 아키텍처 안내

```
modern/api/src/main/java/com/example/
├── item/          문항 · 단원 · 태그 (Item*, Unit*, Tag*)
├── assignment/    과제 배포 · 재배포(Distribution*) · 학급 리포트(Report*, ClassReport)
├── grade/         성적 집계 학급 단원별 현황(GradeReport*) — MS-SQL grades DB 를 GradesJdbc 로 읽는다
├── common/        GlobalExceptionHandler, ErrorResponse, NotFoundException(404), DatabaseUnavailableException(502), ClockConfig
└── config/        WebConfig (CORS: http://localhost:5173 의 GET 만 허용)
modern/api/src/main/resources/application.yml       기본 = 로컬 MariaDB(:3306, itembank DB) + grades.datasource = 로컬 MS-SQL(:1433, grades DB, readonly, 비밀번호는 GRADES_DB_PASSWORD)
modern/api/src/test/resources/application-test.yml  test 프로필 = H2 (MariaDB 모드)

modern/web/src/
├── api/           client.ts(getJson, ApiError) · items.ts · grades.ts(엔드포인트 함수) · types.ts(응답 타입)
├── hooks/         useApiQuery(공통 조회 상태) + useUnits · useUnitItems · useItem · useGradeReport
├── components/    화면 조각 + 같은 폴더의 *.test.tsx
└── test/          mockFetch.ts · fixtures.ts
```

- 화면: `App.tsx` 의 `ModuleTabs` 로 문항 은행(`ItemBrowser`) · 성적 현황(`GradeReportView`)을 전환한다.
- 데이터 흐름: 컴포넌트 → `hooks/use*` → `api/<도메인>.ts` → `getJson` → `modern/api` → Service → Repository → MariaDB.
- `VITE_API_BASE` 가 없으면 `http://localhost:8080` 을 직접 호출하고, `VITE_API_BASE=""` 이면 상대 경로로 Vite 프록시(`/api` → 8080)를 탄다.
- 기존 엔드포인트: `GET /api/units`, `GET /api/units/{code}/items`, `GET /api/items/{id}`, `GET /api/distributions/{id}`, `POST /api/distributions/{id}/redistribute`, `GET /api/classes/{id}/report`, `GET /api/grades/report?class_id=`.

| 레거시 | 현행 |
|---|---|
| `legacy/item-bank-php/` (PHP 7.4, HTML 표 렌더) | `com.example.item` + `modern/web` |
| `legacy/assignment-thymeleaf/` (Spring MVC + JDBC DAO) | `com.example.assignment` (Distribution*) |
| `legacy/grade-mssql/` (로직 대부분이 `sql/usp_*.sql` 저장 프로시저) | `com.example.grade` (`usp_class_report` → GradeReport*) |

- 이관 전후 동작이 같은지는 `characterization/` 의 스냅샷 테스트가 판정한다. 레거시 HTML 과 새 JSON 을 `lib/normalize.mjs` 가 같은 모양으로 바꿔 비교한다.
- 도메인 용어: 문항 `item` · 단원 `unit` · 난이도 `level`(1~5) · 태그 `tag` / 학급 `class` · 과제 `assignment` · 배포 `distribution` · 제출 `submission` / 학생 `STU-<숫자>`.
