# 과제 배포(assignment) 데이터 흐름 · ERD — 배포 등록 화면

> 분석 대상: `legacy/assignment-thymeleaf` · 범위: 배포 등록 화면(`GET /distributions/new`, `POST /distributions`) · 선행 문서: `docs/assignment/ARCHITECTURE.md` · 근거 경로는 저장소 맨 위 기준
>
> 약칭: `P` = `legacy/assignment-thymeleaf/src/main/java/com/example/assign`. 스키마는 모듈 밖 `db/mariadb/init/01-schema.sql` 에 있다.

## 테이블 목록 (이 화면이 닿는 것만)

| 테이블 | 주요 컬럼 | 키 · 제약 | 근거 |
|---|---|---|---|
| `unit` | `id` INT, `code` VARCHAR(16), `name`, `grade` | PK `id`, UNIQUE `code` | `db/mariadb/init/01-schema.sql:11-18` |
| `class` | `id` INT, `name` VARCHAR(50), `teacher_id` VARCHAR(20) | PK `id` | `db/mariadb/init/01-schema.sql:75-80` |
| `assignment` | `id` INT, `title`, `unit_id`, `due_at` DATETIME **NOT NULL**, `status` CHAR(1) 기본 `'O'` | PK `id`, FK `unit_id → unit.id` | `db/mariadb/init/01-schema.sql:82-90` |
| `distribution` | `id` INT(**AUTO_INCREMENT 아님**), `assignment_id`, `class_id`, `distributed_at` DATETIME, `redistributed` TINYINT 기본 0 | PK `id`, 일반 인덱스 2개, FK 2개, **UNIQUE 없음** | `db/mariadb/init/01-schema.sql:92-103` |

- `submission` 테이블(`db/mariadb/init/01-schema.sql:105`)은 이 화면의 코드가 읽거나 쓰지 않는다. 배포 목록의 제출 수 부질의(`P/dao/DistributionDao.java:26`)는 범위 밖 메서드에서만 쓰인다.

## 테이블 관계

```mermaid
erDiagram
    unit ||--o{ assignment : "선언 fk_assignment_unit"
    assignment ||--o{ distribution : "선언 fk_distribution_assignment"
    class ||--o{ distribution : "선언 fk_distribution_class"

    unit { int id PK
           varchar code
           varchar name
           tinyint grade }
    class { int id PK
            varchar name
            varchar teacher_id }
    assignment { int id PK
                 varchar title
                 int unit_id FK
                 datetime due_at
                 char status }
    distribution { int id PK
                   int assignment_id FK
                   int class_id FK
                   datetime distributed_at
                   tinyint redistributed }
```

| 관계 · 제약 | 구분 | 근거 |
|---|---|---|
| `assignment.unit_id → unit.id` | 선언 | `db/mariadb/init/01-schema.sql:89` |
| `distribution.assignment_id → assignment.id` | 선언 | `db/mariadb/init/01-schema.sql:101` |
| `distribution.class_id → class.id` | 선언 | `db/mariadb/init/01-schema.sql:102` |
| `(assignment_id, class_id)` 는 한 건만 | **추정(코드로만 막음)** | 스키마는 일반 KEY 뿐(`db/mariadb/init/01-schema.sql:99-100`). 코드 주석 · COUNT 확인(`P/dao/DistributionDao.java:46-50`, `P/service/DistributionService.java:59`) |
| `distribution.id` 는 `MAX(id)+1` 로 채번 | 추정(코드로만) | 스키마에 AUTO_INCREMENT 없음(`db/mariadb/init/01-schema.sql:93`), 채번 `P/dao/DistributionDao.java:55-56` |

- `assignment` ⟕ `unit` 은 LEFT JOIN 이지만(`P/dao/AssignmentDao.java:24`) `unit_id` 가 NOT NULL + FK 라 짝 없는 행은 스키마상 생기지 않는다 (`db/mariadb/init/01-schema.sql:85`, `:89`).

## 데이터 흐름

```
[GET /distributions/new]
   assignment ⟕ unit (+ distribution 부질의 class_cnt), status IN (O,X,C) → 과제 select
   class 전체 → 학급 select
   AppClock.now() → 마감 옵션 비활성 판단 (화면)

[POST /distributions]
   assignment 1건 조회 → (컨트롤러) 마감 확인
   → (서비스, 트랜잭션) assignment 재조회 → class 조회 → 삭제 상태 확인
   → distribution COUNT(과제, 학급) → MAX(id)+1 → distribution INSERT
```

- 근거: `P/web/DistributionController.java:40-77`, `P/service/DistributionService.java:46-68`, `P/dao/AssignmentDao.java:34-48`, `P/dao/ClassDao.java:20-30`, `P/dao/DistributionDao.java:47-62`
- 과제 조회가 컨트롤러(`:53`)와 서비스(`:48`)에서 두 번 일어나고, 컨트롤러 쪽은 트랜잭션 밖이다.
- `class_cnt`(`P/dao/AssignmentDao.java:23`)는 이 화면 템플릿에서 쓰이지 않는다 (`legacy/assignment-thymeleaf/src/main/resources/templates/distribution_form.html:14-18`).

## 읽기 · 쓰기 위치 표

| 테이블 | 읽기(SELECT) | 쓰기(INSERT · UPDATE · DELETE) |
|---|---|---|
| `unit` | `AssignmentDao.findAllForSelect` · `findById` — `P/dao/AssignmentDao.java:22-24` | 없음 |
| `class` | `ClassDao.findAll` — `P/dao/ClassDao.java:21` / `ClassDao.findById` — `:25` | 없음 |
| `assignment` | `AssignmentDao.findAllForSelect` — `P/dao/AssignmentDao.java:34-38` / `findById` — `:41-43` | 없음 |
| `distribution` | `AssignmentDao` 부질의 `class_cnt` — `P/dao/AssignmentDao.java:23` / `countByAssignmentAndClass` — `P/dao/DistributionDao.java:47-50` / `nextId` — `:54-55` | INSERT `DistributionDao.insert` — `P/dao/DistributionDao.java:59-62` |

## 미확인

- 실제 데이터에 `status` 가 `X` · `D` 인 과제가 있는지: 스키마 주석은 `O` · `C` 만 적고 CHECK 제약이 없다(`db/mariadb/init/01-schema.sql:87`). 시드는 범위 밖이라 읽지 않았다.
- `distribution` 에 같은 (과제, 학급) 행이 이미 중복돼 있는지: DB 를 조회하지 않았다.
- 다른 모듈(성적 · 문항 은행)이 `distribution` 을 읽거나 쓰는지: 범위 밖.
