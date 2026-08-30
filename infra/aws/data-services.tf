resource "aws_db_subnet_group" "main" {
  name       = local.name
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_db_instance" "main" {
  identifier                      = local.name
  engine                          = "postgres"
  engine_version                  = "17"
  instance_class                  = "db.t4g.medium"
  allocated_storage               = 40
  max_allocated_storage           = 200
  storage_type                    = "gp3"
  storage_encrypted               = true
  db_name                         = "ticketing"
  username                        = var.database_username
  password                        = var.database_password
  db_subnet_group_name            = aws_db_subnet_group.main.name
  vpc_security_group_ids          = [aws_security_group.database.id]
  publicly_accessible             = false
  multi_az                        = true
  backup_retention_period         = 14
  deletion_protection             = true
  performance_insights_enabled    = true
  auto_minor_version_upgrade      = true
  apply_immediately               = false
  skip_final_snapshot             = false
  final_snapshot_identifier       = "${local.name}-final"
  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]
}

resource "aws_elasticache_subnet_group" "main" {
  name       = local.name
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_elasticache_replication_group" "main" {
  replication_group_id       = local.name
  description                = "SeatForge availability cache and rate limiter"
  engine                     = "redis"
  engine_version             = "7.1"
  node_type                  = "cache.t4g.small"
  port                       = 6379
  num_cache_clusters         = 2
  automatic_failover_enabled = true
  multi_az_enabled           = true
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  subnet_group_name          = aws_elasticache_subnet_group.main.name
  security_group_ids         = [aws_security_group.redis.id]
  snapshot_retention_limit   = 3
}

resource "aws_mq_broker" "main" {
  broker_name                = local.name
  engine_type                = "RABBITMQ"
  engine_version             = var.rabbitmq_engine_version
  host_instance_type         = "mq.m5.large"
  deployment_mode            = "ACTIVE_STANDBY_MULTI_AZ"
  publicly_accessible        = false
  auto_minor_version_upgrade = true
  subnet_ids                 = aws_subnet.private[*].id
  security_groups            = [aws_security_group.rabbitmq.id]

  user {
    username = var.rabbitmq_username
    password = var.rabbitmq_password
  }

  logs {
    general = true
  }
}

resource "aws_secretsmanager_secret" "application" {
  name                    = "${local.name}/application"
  recovery_window_in_days = 30
}

resource "aws_secretsmanager_secret_version" "application" {
  secret_id = aws_secretsmanager_secret.application.id
  secret_string = jsonencode({
    database_password = var.database_password
    rabbitmq_password = var.rabbitmq_password
    jwt_secret        = var.jwt_secret
  })
}
