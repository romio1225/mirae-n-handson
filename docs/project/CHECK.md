# 완성 기준 점검 — 데이터 · 인프라(IaC) / 이성만

1. 트랙 완성 기준 ① IaC 생성: 충족
   증거(실행한 명령과 결과 한 줄): `pipeline-samples/terraform/monitoring.tf` 에 `aws_cloudwatch_metric_alarm` 1개(`grep -c '^resource '` → `1`). `terraform fmt -check` → 종료 코드 0, 저장소 밖 임시 복사본에서 `terraform init -backend=false` 뒤 `terraform validate` → `Success! The configuration is valid.`. 기존 `main.tf` · `variables.tf` 의 `git diff --stat day4-start..HEAD` 는 0줄이고, `git ls-files` 에 tfstate · tfplan · .terraform 은 0건이다.
2. 트랙 완성 기준 ② 리뷰 1회: 충족
   증거(파일 경로 또는 PR 주소): `docs/project/iac-review.md` 의 첫 줄은 `판정: 승인` 이고 지적은 0건이다(`iac-reviewer` Sub-agent, 2026-10-20, 1회).
3. /verify 판정: 승인
   증거(판정 요약 한 줄, 치명 건수): `/verify day4-start..HEAD` → 승인. 테스트 통과(fmt · validate) · 치명 이슈 0건 · 범위 이탈 없음 · 컨벤션 준수. 치명 0건, 경고 1건, 제안 4건(주석 단위 "20 GB" → GiB, 설명 문구 임계값 하드코딩, 리소스 파일 분리, 리뷰 대상 표기)은 고치지 않았다. 따로 돌린 `reviewer` 는 fmt · validate 결과를 직접 보지 못해 "반려(확인 필요)"를 냈다. 그 밖에는 치명 0건이었고, 1번의 실행 결과로 해소했다.

범위를 줄였는가: 예 — 뺀 항목과 이유는 DESIGN.md "3. 범위 — 하지 않는다"에 있음
팀 저장소에 가져갈 파일: 없음 (오늘은 새 Skill · Hook · Sub-agent 를 만들지 않았다. `iac-reviewer` 는 이미 팀 기준 한 벌에 있다.)
