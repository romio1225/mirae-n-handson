# 리뷰 1 응답 (만든 세션)

대상: `docs/verify/migration-review-1.md`. 최종 판단은 사람(이성만)이 했다.

| # | 심각도 | 분류 | 이유 |
|---|---|---|---|
| 1 | 경고 | 반박 | 웹 화면은 이관과 별개 요청으로 따로 커밋했다(`4b53884`). 리뷰 범위 `upstream/main...HEAD` 에 1회차 결과물 전체가 섞여서 생긴 지적이라, 코드를 고칠 문제가 아니라 범위를 좁힐 문제다 |
| 2 | 경고 | 반박(사람 승인 확인) | `build.gradle:27-28` · `application.yml:28-37` · `db/mssql/init/02-seed.sql:224-257` 은 1회차에 사람이 승인한 변경이다(2026-10-06 재확인) |
| 3 | 경고 | 반박 | #1 과 같은 이유. 문서 · Skill · item-bank 베이스라인은 별도 커밋(`5cb2c6a` · `2c07002` · `e002d4f`)의 다른 요청이다 |
| 4 | 경고 | 수용(코드 아닌 규칙 쪽 수정) | 코드는 맞다(`GradeReportService.java:23-24`). 붙이면 MS-SQL 만 읽는데 MariaDB 연결을 잡는다. 사람 승인을 받아 `CLAUDE.md:36-37` 에 예외 규칙을 추가했다 |
| 5 | 제안 | 목록만 | 쿼리 정합성은 characterization grade 16건이 검증한다 |
| 6 | 제안 | 목록만 | 코드 변경 없음. 레거시 item-bank(8081)는 `php` 프로필로 기동해 전체 실행을 가능하게 했다 |
| 7 | 제안 | 목록만 | `trustServerCertificate=true` 는 로컬 compose 용 설정이다 |

수정 파일: `CLAUDE.md` 1곳.
