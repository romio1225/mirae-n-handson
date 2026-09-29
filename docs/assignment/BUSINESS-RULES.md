# 과제 배포(assignment) 비즈니스 규칙 후보 — 배포 등록 화면

> 분석 대상: `legacy/assignment-thymeleaf` · 범위: 배포 등록 화면(`GET /distributions/new`, `POST /distributions`)과 그 화면이 부르는 코드 · 선행 문서: `docs/assignment/ARCHITECTURE.md`, `docs/assignment/ERD.md` · 근거 경로는 저장소 맨 위 기준
>
> 특히 본 곳: 배포 제외 조건(마감 · 상태값), 날짜 비교, 중복 방지, 채번, 오류 처리
>
> 약칭: `P` = `legacy/assignment-thymeleaf/src/main/java/com/example/assign`, `CTL` = `P/web/DistributionController.java`, `SVC` = `P/service/DistributionService.java`, `FORM` = `legacy/assignment-thymeleaf/src/main/resources/templates/distribution_form.html`. 근거 칸에는 전체 경로를 적는다.

## 1. 배포 가능 여부

### BR-01
- 규칙: 과제 마감 시각이 현재 시각보다 **앞서면**(마감이 지났으면) 상태가 `'X'` 가 아닌 한 배포를 거부하고 "마감(…)이 지난 과제는 배포할 수 없습니다." 를 보여 준다.
- 근거: `legacy/assignment-thymeleaf/src/main/java/com/example/assign/web/DistributionController.java:60-64`
- 근거 코드:
    ```java
            if (a.getDueAt() != null && a.getDueAt().isBefore(now)) {
                if (!"X".equals(a.getStatus())) {
    ```
- 확신도: 확실
- 비고: `isBefore` 라서 마감 시각과 현재 시각이 **같으면 허용**된다. `due_at` 은 NOT NULL(`db/mariadb/init/01-schema.sql:86`)이라 `!= null` 분기는 도달하지 않는다. `X` 의 뜻 "연장"은 주석(`CTL:59`)과 화면 표시(`FORM:18`)에 있다.

### BR-02
- 규칙: 마감 확인은 **컨트롤러에만** 있고 서비스 `distribute` 에는 없다. 다른 경로로 `distribute` 를 부르면 마감이 지나도 배포된다.
- 근거: `legacy/assignment-thymeleaf/src/main/java/com/example/assign/web/DistributionController.java:58-65`, `legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:47-68`
- 근거 코드 (`SVC:56`. `distribute` 안의 상태 확인은 이 한 줄뿐이고 `due_at` 비교가 없다):
    ```java
            if ("D".equals(a.getStatus())) {
    ```
- 확신도: 확실(조건이 없음을 확인). 이 화면 밖에서 `distribute` 를 부르는 곳이 있는지는 범위 밖이라 미확인.
- 비고: 컨트롤러 확인은 트랜잭션 밖에서 한다(`SVC:46` 의 `@Transactional` 은 서비스에만).

### BR-03
- 규칙: 상태가 `'D'`(삭제)인 과제는 배포를 거부한다("삭제된 과제는 배포할 수 없습니다.").
- 근거: `legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:56-58`
- 근거 코드:
    ```java
            if ("D".equals(a.getStatus())) {
    ```
- 확신도: 확실
- 비고: 컨트롤러의 마감 확인(BR-01)이 먼저 돌기 때문에, 삭제 과제이면서 마감이 지났으면 "삭제" 대신 "마감" 메시지가 나온다(`CTL:60-63` 이 `CTL:67` 보다 먼저). `D` 는 스키마 주석(`O=진행 C=마감`, `db/mariadb/init/01-schema.sql:87`)에 없는 값이다.

### BR-04
- 규칙: 상태 `'C'`(마감)는 배포 가부에 쓰이지 않는다. 상태가 `C` 여도 `due_at` 이 미래이면 선택 · 배포된다.
- 근거: `legacy/assignment-thymeleaf/src/main/java/com/example/assign/web/DistributionController.java:60-61`, `legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:56`, `legacy/assignment-thymeleaf/src/main/java/com/example/assign/dao/AssignmentDao.java:36`
- 근거 코드 (`AssignmentDao.java:36`. 선택 목록에 `C` 가 포함된다):
    ```java
                    + " WHERE a.status IN ('O', 'X', 'C') "
    ```
- 확신도: 확실(범위 안 코드에 `"C"` 비교가 없음)
- 비고: 마감 여부는 상태값이 아니라 `due_at` 과 현재 시각으로 판단한다(BR-01).

### BR-05
- 규칙: 같은 과제가 같은 학급에 이미 배포돼 있으면(행이 1건 이상) 새 배포를 거부하고 재배포를 쓰라고 안내한다.
- 근거: `legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:59-61`, `legacy/assignment-thymeleaf/src/main/java/com/example/assign/dao/DistributionDao.java:47-51`
- 근거 코드:
    ```java
            if (distributionDao.countByAssignmentAndClass(assignmentId, classId) > 0) {
                    "SELECT COUNT(*) FROM distribution WHERE assignment_id = ? AND class_id = ?",
    ```
- 확신도: 확실
- 비고: `redistributed` 값과 상관없이 센다(조건 없음). DB 에 UNIQUE 제약은 없고 코드로만 막는다(`DistributionDao.java:46` 주석, `db/mariadb/init/01-schema.sql:99-100`). 두 요청이 동시에 오면 둘 다 통과할 수 있다 — 추정, 재현하지 않음.

### BR-06
- 규칙: 과제가 없으면 "존재하지 않는 과제입니다." 로 폼에 돌아간다.
- 근거: `legacy/assignment-thymeleaf/src/main/java/com/example/assign/web/DistributionController.java:53-57`, `legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:48-51`
- 근거 코드 (`CTL:55`):
    ```java
                ra.addFlashAttribute("error", "존재하지 않는 과제입니다.");
    ```
- 확신도: 확실
- 비고: 서비스에도 같은 확인이 있고 메시지에 `(id=…)` 가 붙는다(`SVC:50`). 컨트롤러가 먼저 걸러서 이 화면에서는 서비스 메시지가 보통 나오지 않는다.

### BR-07
- 규칙: 학급이 없으면 "존재하지 않는 학급입니다. (id=…)" 로 폼에 돌아간다.
- 근거: `legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:52-55`, `legacy/assignment-thymeleaf/src/main/java/com/example/assign/web/DistributionController.java:69-71`
- 근거 코드:
    ```java
                throw new IllegalArgumentException("존재하지 않는 학급입니다. (id=" + classId + ")");
    ```
- 확신도: 확실
- 비고: 학급은 컨트롤러에서 미리 확인하지 않는다(과제와 다름).

## 2. 날짜 · 시각

### BR-08
- 규칙: "현재 시각"은 `2026-09-15 00:00:00` 고정이고, 시스템 속성 `app.clock` 이 `system` 일 때만 실제 시각(나노초 0)을 쓴다. 마감 판정(BR-01)과 배포 시각(BR-10)이 모두 이 값을 쓴다.
- 근거: `legacy/assignment-thymeleaf/src/main/java/com/example/assign/AppClock.java:9`, `:14-20`
- 근거 코드:
    ```java
        private static final String FIXED = "2026-09-15 00:00:00";
            if (sys != null && sys.equals("system")) {
    ```
- 확신도: 확실
- 비고: 주석은 "운영 반영 전 LocalDateTime.now() 로 되돌릴 것 (아직 안 되돌림)"(`AppClock.java:8`). 운영에서 `app.clock=system` 을 넣는지는 미확인.

### BR-09
- 규칙: 마감 오류 메시지의 마감 시각은 `yyyy-MM-dd HH:mm` 형식이고, 과제 선택 목록의 마감은 `MM-dd HH:mm` 형식이다.
- 근거: `legacy/assignment-thymeleaf/src/main/java/com/example/assign/web/DistributionController.java:62`, `legacy/assignment-thymeleaf/src/main/java/com/example/assign/AppClock.java:12`, `:22-27`, `legacy/assignment-thymeleaf/src/main/resources/templates/distribution_form.html:17`
- 근거 코드 (`AppClock.java:12`):
    ```java
        public static final DateTimeFormatter FMT_SHORT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    ```
- 확신도: 확실
- 비고: 같은 마감 시각이 두 형식으로 보인다(연도 유무).

## 3. 저장

### BR-10
- 규칙: 새 배포 행은 `distributed_at = 현재 시각(AppClock)`, `redistributed = 0` 으로 넣는다.
- 근거: `legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:63`, `legacy/assignment-thymeleaf/src/main/java/com/example/assign/dao/DistributionDao.java:59-62`
- 근거 코드:
    ```java
            int n = distributionDao.insert(id, assignmentId, classId, AppClock.now());
                    "INSERT INTO distribution (id, assignment_id, class_id, distributed_at, redistributed) VALUES (?, ?, ?, ?, 0)",
    ```
- 확신도: 확실
- 비고: 매직 넘버 `0`. 스키마 기본값도 0(`db/mariadb/init/01-schema.sql:97`).

### BR-11
- 규칙: 새 배포 id 는 `distribution` 의 `MAX(id) + 1` 이고, 행이 없으면 1 이다.
- 근거: `legacy/assignment-thymeleaf/src/main/java/com/example/assign/dao/DistributionDao.java:54-57`
- 근거 코드:
    ```java
            Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM distribution", Long.class);
            return (max == null ? 0L : max.longValue()) + 1L;
    ```
- 확신도: 확실
- 비고: `id` 는 AUTO_INCREMENT 가 아니다(`db/mariadb/init/01-schema.sql:93`). 동시 요청이면 같은 id 로 PK 충돌이 날 수 있고, 그 경우 BR-13 의 "DB 오류" 메시지가 나올 것으로 보인다 — 추정, 재현하지 않음.

### BR-12
- 규칙: INSERT 결과가 정확히 1행이 아니면 "배포 저장에 실패했습니다." 로 실패 처리한다. 성공하면 "배포가 등록되었습니다. (배포 #id)" 를 들고 배포 목록으로 간다.
- 근거: `legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:64-66`, `legacy/assignment-thymeleaf/src/main/java/com/example/assign/web/DistributionController.java:67-68`, `:76`
- 근거 코드:
    ```java
            if (n != 1) {
                ra.addFlashAttribute("message", "배포가 등록되었습니다. (배포 #" + id + ")");
    ```
- 확신도: 확실

## 4. 오류 처리

### BR-13
- 규칙: 배포 제출 중 DB 오류가 나면 원인을 숨기고 "DB 오류로 배포에 실패했습니다." 로 폼에 돌아간다. `IllegalArgumentException` · `IllegalStateException` 은 예외 메시지를 그대로 보여 준다.
- 근거: `legacy/assignment-thymeleaf/src/main/java/com/example/assign/web/DistributionController.java:66-75`
- 근거 코드:
    ```java
            } catch (IllegalArgumentException | IllegalStateException e) {
                ra.addFlashAttribute("error", "DB 오류로 배포에 실패했습니다.");
    ```
- 확신도: 확실
- 비고: `CTL:53` 의 과제 조회는 `try` 밖이라, 여기서 난 DB 오류는 BR-14 경로로 간다.

### BR-14
- 규칙: 폼 화면(GET)이나 `try` 밖에서 난 DB 오류는 `db_error` 화면으로 가고, 원인 메시지를 화면에 넘기며 표준 출력에 찍는다.
- 근거: `legacy/assignment-thymeleaf/src/main/java/com/example/assign/web/DbErrorAdvice.java:11-17`
- 근거 코드:
    ```java
            System.out.println("[DB-ERROR] " + e.getClass().getSimpleName() + " : " + e.getMostSpecificCause().getMessage());
            model.addAttribute("detail", e.getMostSpecificCause().getMessage());
    ```
- 확신도: 확실
- 비고: `db_error.html` 이 `detail` 을 실제로 보여 주는지는 읽지 않았다(범위 밖 템플릿).

## 5. 화면 표시

### BR-15
- 규칙: 과제 선택 목록은 상태 `O` · `X` · `C` 과제를 마감 빠른 순, 같으면 id 순으로 보여 준다. 마감이 지나고 `X` 가 아닌 과제는 비활성(선택 불가)이고 `[마감]`, 마감이 지난 `X` 과제는 `[연장]` 을 붙인다.
- 근거: `legacy/assignment-thymeleaf/src/main/java/com/example/assign/dao/AssignmentDao.java:34-39`, `legacy/assignment-thymeleaf/src/main/resources/templates/distribution_form.html:14-18`
- 근거 코드:
    ```java
                    + " ORDER BY a.due_at ASC, a.id ASC";
    ```
    ```html
                        th:disabled="${a.dueAt != null and a.dueAt.isBefore(now) and a.status != 'X'}"
    ```
- 확신도: 확실
- 비고: 비활성 조건은 BR-01 과 같은 식이다. 화면에서 비활성이어도 POST 를 직접 보내면 BR-01 이 다시 막는다.

### BR-16
- 규칙: 학급 선택 목록은 전체 학급을 id 순으로 보여 주고 "이름 (교사 ID)" 로 표시한다. 학급에는 거르는 조건이 없다.
- 근거: `legacy/assignment-thymeleaf/src/main/java/com/example/assign/dao/ClassDao.java:21`, `legacy/assignment-thymeleaf/src/main/resources/templates/distribution_form.html:25`
- 근거 코드:
    ```java
            return jdbc.query("SELECT id, name, teacher_id FROM `class` ORDER BY id ASC", MAPPER);
    ```
- 확신도: 확실

## 매직 넘버

| 값 | 위치 | 추정 의미 |
|---|---|---|
| `'X'` | `CTL:61`, `FORM:16`, `FORM:18`, `P/dao/AssignmentDao.java:36` | 연장 — 마감 지나도 배포 허용 |
| `'D'` | `SVC:56` | 삭제 — 배포 불가 |
| `'O'`, `'C'` | `P/dao/AssignmentDao.java:36` | 진행 · 마감(스키마 주석 `db/mariadb/init/01-schema.sql:87`) — 선택 목록 포함 |
| `"2026-09-15 00:00:00"` | `P/AppClock.java:9` | 고정 현재 시각 |
| `"system"` | `P/AppClock.java:16` | 실제 시각 사용 스위치 |
| `0` (redistributed) | `P/dao/DistributionDao.java:61` | 재배포 아님 |
| `1` (`n != 1`) | `SVC:64` | INSERT 기대 행 수 |
| `0` / `+ 1L` | `P/dao/DistributionDao.java:55-56` | 첫 id 와 채번 증가분 |

## 같은 규칙이 두 곳에 다르게 있는 곳

| 주제 | 곳 A | 곳 B | 차이 |
|---|---|---|---|
| 마감 판정 | 컨트롤러 `CTL:60-61` | 템플릿 `FORM:16` | 식은 같다. 서비스에는 없다(BR-02) |
| 과제 존재 확인 | 컨트롤러 `CTL:53-55` | 서비스 `SVC:48-50` | 메시지에 id 유무가 다르다 |
| 마감 표시 형식 | 오류 메시지 `yyyy-MM-dd HH:mm` (`CTL:62`) | 선택 목록 `MM-dd HH:mm` (`FORM:17`) | 연도 유무 |
| DB 오류 | POST `try` 안: 원인 숨김(`CTL:72-74`) | GET · `try` 밖: 원인 노출(`P/web/DbErrorAdvice.java:15`) | 노출 여부 |

## 주석만 있는 것 (규칙으로 올리지 않음)

- 과제 상태 `O=진행 C=마감` — `db/mariadb/init/01-schema.sql:87`. 코드는 `X` · `D` 도 쓰고 스키마에 CHECK 제약이 없다.
- "운영 반영 전 LocalDateTime.now() 로 되돌릴 것" — `P/AppClock.java:8`. 코드는 여전히 고정 시각(BR-08).
- "같은 학급 + 같은 과제 는 한 건만 존재해야 한다" — `P/dao/DistributionDao.java:46`. DB 는 강제하지 않고 서비스의 COUNT 로만 막는다(BR-05).

## 검증 이력

| 날짜 | 규칙 | 뽑은 기준 | 판정 | 조치 |
|---|---|---|---|---|
