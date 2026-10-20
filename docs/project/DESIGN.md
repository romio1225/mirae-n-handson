# 실전 프로젝트 설계 — 데이터 · 인프라(IaC) / 이성만

## 1. 목표
문항 은행 dev DB(`aws_db_instance.itembank`)에 CloudWatch 경보 리소스 하나를 Terraform 으로 추가하고, `iac-reviewer` 리뷰 1회로 승인 · 반려 판정을 `docs/project/iac-review.md` 에 남긴다.

## 2. 재료
| 재료 | 있음/없음 | 경로 |
|---|---|---|
| 대상 Terraform 코드 (RDS MariaDB · 보안 그룹 · 서브넷 그룹) | 있음 | `pipeline-samples/terraform/main.tf`, `pipeline-samples/terraform/variables.tf` |
| IaC 리뷰 Sub-agent | 있음 | `.claude/agents/iac-reviewer.md` |
| IaC 팀 규칙 템플릿 (명령 · 컨벤션 · 완료 기준) | 있음 | `templates/CLAUDE.iac.md` |
| 위험 명령 차단 Hook · 권한 (`terraform apply` · `destroy` deny) | 있음 | `hooks/block-dangerous.mjs`, `.claude/settings.json:14-15` |
| 검증 Skill | 있음 | `.claude/skills/verify/SKILL.md` |
| Terraform CLI | 있음 | `/opt/homebrew/bin/terraform` (v1.16.5) |
| 정적 분석 도구 (tflint · checkov) | 없음 | 설치하지 않음. 범위 밖 |
| 추가할 경보 리소스 | 없음 → 새로 만들 것 | `pipeline-samples/terraform/monitoring.tf` |
| 리뷰 결과 문서 | 없음 → 새로 만들 것 | `docs/project/iac-review.md` |

## 3. 범위
### 한다
1. `pipeline-samples/terraform/monitoring.tf` 를 새로 만들어 `aws_cloudwatch_metric_alarm` 리소스 1개(dev DB 남은 저장 공간 경보)를 추가한다. 기존 `main.tf` · `variables.tf` 는 고치지 않는다.
2. `terraform fmt -check` · `terraform init -backend=false` · `terraform validate` 로 로컬 검증만 한다.
3. `iac-reviewer` 를 1회 실행해 판정과 지적 표를 `docs/project/iac-review.md` 에 저장한다.

### 하지 않는다
- `terraform plan` · `apply` — 실제 AWS 계정에 접속하지 않는다는 규칙(`templates/CLAUDE.iac.md`, `pipeline-samples/README.md`)이 있고, 완성 기준은 "생성 + 리뷰"라 plan 이 없어도 판정할 수 있다.
- 경보 알림 대상(SNS 토픽 · 구독) 추가 — 리소스가 2개 이상이 되어 최소 범위("리소스 하나")를 넘는다. 경보는 `alarm_actions` 없이도 생성 · 리뷰할 수 있다.
- CPU · 연결 수 등 다른 지표 경보 — 같은 이유로 리소스 하나에 집중한다.
- `envs/dev` · `modules/` 구조로 재편, 보안 그룹 인라인 규칙을 `aws_security_group_rule` 로 분리 — 템플릿 컨벤션과 다르지만 기존 코드 리팩토링은 이번 목표가 아니다.
- `.gitignore` 에 `.terraform/` · `*.tfstate*` 추가 — 요청 범위 밖 파일이다. 대신 init · validate 를 저장소 밖 임시 복사본에서 실행해 부산물이 저장소에 생기지 않게 한다.
- tflint · checkov 설치와 실행 — 새 도구 설치는 범위 밖이고, 리뷰는 `iac-reviewer` 1회로 충분하다.

## 4. 미해결 질문
설계 시점에 남았던 질문 3개를 아래처럼 정리했다. 남은 미해결 질문은 없다.

- ~~`pipeline-samples/README.md` 의 "실습 중 고친 내용은 끝나기 전에 되돌린다"와 산출물 커밋이 충돌하는가~~ → **충돌하지 않는 것으로 정리.** 그 규칙은 Day 3-2 실습 3 · 4 가 기존 `main.tf` · `variables.tf` 를 일부러 망가뜨렸다가 원상 복구하라는 뜻이다. 이번에는 새 파일 `monitoring.tf` 만 더하고 두 파일은 건드리지 않는다(완성 기준 4번으로 확인). Day 4-1 은 "완성 기준을 충족한 상태로 커밋"이 산출물이므로 커밋해 둔다.
- ~~경보 임계값의 근거~~ → **실습용 더미 값으로 확정.** `allocated_storage = 20`(GB)의 10%인 2 GiB(2147483648 바이트)를 `locals` 의 이름 붙인 값으로 두고, 주석에 "실습용 dev 기본값, 운영 기준 아님"이라고 적는다.
- ~~`terraform init` 의 네트워크 접속 가능 여부~~ → **확인함.** 2026-10-20 저장소 밖 임시 복사본에서 `terraform init -backend=false` 가 provider 를 받았고 기존 코드의 `terraform validate` 가 `Success! The configuration is valid.` 를 출력했다. 단계 1도 같은 방식(임시 복사본에서 init · validate)으로 해서 `.terraform/` · `.terraform.lock.hcl` 이 저장소에 생기지 않게 한다.

## 5. 단계
**단계 1 — 경보 리소스 생성**
- 할 일: dev DB 의 `FreeStorageSpace` 경보 리소스 1개를 작성한다. DB 식별자는 `aws_db_instance.itembank.identifier` 로 참조하고, 비밀값 · 0.0.0.0/0 · 교체 유발 속성 변경은 넣지 않는다.
- 만들 파일: `pipeline-samples/terraform/monitoring.tf`
- 확인 방법:
  ```bash
  cd pipeline-samples/terraform && terraform fmt -check && echo FMT_OK && cd -
  T="$(mktemp -d)" && cp pipeline-samples/terraform/*.tf "$T"/ && (cd "$T" && terraform init -backend=false -input=false >/dev/null && terraform validate)
  git status --short pipeline-samples/terraform/
  ```
  `FMT_OK`, `Success! The configuration is valid.` 가 보이고, git status 에 `?? pipeline-samples/terraform/monitoring.tf` 한 줄만 보이면 통과다.

**단계 2 — IaC 리뷰 1회**
- 할 일: `iac-reviewer` Sub-agent 에 `day4-start..HEAD` 의 `pipeline-samples/terraform/` 변경만 넘겨 검토를 받고, 결과를 그대로 저장한다. 반려면 지적을 고친 뒤 한 번 더 리뷰하고, 두 결과를 모두 남긴다.
- 만들 파일: `docs/project/iac-review.md`
- 확인 방법: `head -1 docs/project/iac-review.md` 가 `판정: 승인` 또는 `판정: 반려` 로 시작하고, 지적이 있으면 표에 `파일:줄번호` 열이 있다.

**단계 3 — 커밋**
- 할 일: 생성 코드와 리뷰 결과를 커밋한다.
- 만들거나 고칠 파일: 없음 (커밋만)
- 확인 방법: `git diff --stat day4-start..HEAD` 에 `docs/project/DESIGN.md`, `pipeline-samples/terraform/monitoring.tf`, `docs/project/iac-review.md` 만 보이고 `main.tf` · `variables.tf` 는 보이지 않는다.

## 6. 완성 기준 (Day 4-1에서 점검)
- [ ] `pipeline-samples/terraform/monitoring.tf` 에 `resource "aws_cloudwatch_metric_alarm"` 블록이 정확히 1개 있다 (`grep -c '^resource ' pipeline-samples/terraform/monitoring.tf` → `1`).
- [ ] `terraform fmt -check` 가 종료 코드 0 이고 `terraform validate` 가 `Success! The configuration is valid.` 를 출력한다.
- [ ] `docs/project/iac-review.md` 첫 줄이 `판정: 승인` 이다. 반려였다면 수정 후 재리뷰 결과까지 남아 있다.
- [ ] `git diff --stat day4-start..HEAD -- pipeline-samples/terraform/main.tf pipeline-samples/terraform/variables.tf` 출력이 비어 있다 (기존 리소스 변경 · 교체 없음).
- [ ] 저장소에 `.terraform/` · `*.tfstate` · `tfplan` · `.terraform.lock.hcl` 이 커밋되지 않았다 (`git ls-files | grep -E 'tfstate|tfplan|\.terraform'` 출력 없음).
