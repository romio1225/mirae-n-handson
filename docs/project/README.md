문항 은행 dev DB(RDS MariaDB)에 남은 저장 공간 경보(CloudWatch)를 Terraform 으로 추가하고, 로컬 검증(fmt · validate)과 `iac-reviewer` Sub-agent 리뷰로 적용 전 승인 · 반려를 판정합니다.
저장소 루트에서 `(cd pipeline-samples/terraform && terraform fmt -check && T="$(mktemp -d)" && cp *.tf "$T"/ && cd "$T" && terraform init -backend=false -input=false >/dev/null && terraform validate)` 를 실행한 뒤, Claude Code 에서 "pipeline-samples/terraform/monitoring.tf 를 iac-reviewer Sub-agent 에게 검토받아 줘"라고 요청합니다.
plan · apply 는 하지 않았고 경보에 알림 대상(`alarm_actions`)이 없습니다. 임계값 2 GiB 는 실습용 더미 값입니다. 현업에 옮길 때는 실제 용량 기준 임계값, SNS 알림 연결, 원격 backend 의 plan 검토를 더해야 합니다.
