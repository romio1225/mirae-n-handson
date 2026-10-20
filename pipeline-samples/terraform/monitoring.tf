# 문항 은행(itembank) dev DB 모니터링 — 실습용 더미 코드. 실제 AWS 계정에 적용하지 않는다.

locals {
  # 실습용 dev 기본값, 운영 기준 아님: allocated_storage 20 GB 의 10% = 2 GiB
  db_free_storage_threshold_bytes = 2 * 1024 * 1024 * 1024
  db_alarm_period_seconds         = 300
  db_alarm_evaluation_periods     = 3
}

# 남은 저장 공간이 임계값 아래로 15분(5분 x 3회) 머물면 경보
resource "aws_cloudwatch_metric_alarm" "itembank_db_free_storage_low" {
  alarm_name        = "itembank-dev-db-free-storage-low"
  alarm_description = "itembank dev DB 남은 저장 공간이 2 GiB 미만"

  namespace   = "AWS/RDS"
  metric_name = "FreeStorageSpace"
  dimensions = {
    DBInstanceIdentifier = aws_db_instance.itembank.identifier
  }

  statistic           = "Minimum"
  period              = local.db_alarm_period_seconds
  evaluation_periods  = local.db_alarm_evaluation_periods
  comparison_operator = "LessThanThreshold"
  threshold           = local.db_free_storage_threshold_bytes
  treat_missing_data  = "missing"
}
