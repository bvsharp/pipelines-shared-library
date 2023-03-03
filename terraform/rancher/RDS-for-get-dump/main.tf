variable "arn_db_snapshot" {
  type        = string
  default     = "arn:aws:rds:us-west-2:732722833398:cluster-snapshot:nbf-bugfest-snapshot-11-07-2022"
  description = ""
}

data "aws_db_cluster_snapshot" "latest_prod_snapshot" {
  db_cluster_snapshot_identifier = var.arn_db_snapshot
}

resource "aws_rds_cluster" "aurora" {
  cluster_identifier   = "temporary-rds-cluster-destroy-during-hour"
  snapshot_identifier  = data.aws_db_cluster_snapshot.latest_prod_snapshot.id
  db_subnet_group_name = aws_db_subnet_group.default.name
  engine               = data.aws_db_cluster_snapshot.latest_prod_snapshot.engine
  engine_version       = data.aws_db_cluster_snapshot.latest_prod_snapshot.engine_version
  master_username      = "folio"
  master_password      = local.pg_password
  skip_final_snapshot  = true
  database_name = "folio"
}

resource "aws_rds_cluster_instance" "aurora" {
  cluster_identifier   = aws_rds_cluster.aurora.id
  instance_class       = "db.r5.xlarge"//"db.t2.small"
  db_subnet_group_name = aws_db_subnet_group.default.name
  engine               = data.aws_db_cluster_snapshot.latest_prod_snapshot.engine
  engine_version       = data.aws_db_cluster_snapshot.latest_prod_snapshot.engine_version
}

resource "aws_db_subnet_group" "default" {
  name       = "mykhailo-test-should-destroy"
  subnet_ids = data.aws_subnets.database.ids

  tags = {
    Name = "My DB subnet group"
  }
}

resource "random_password" "pg_password" {
  length           = 16
  special          = true
  numeric          = true
  upper            = true
  lower            = true
  min_lower        = 2
  min_numeric      = 2
  min_special      = 2
  min_upper        = 2
  override_special = "‘~!@#$%^&*()_-+={}[]\\/<>,.;?':|"
}

locals {
  pg_password = var.pg_password == "" ? random_password.pg_password.result : var.pg_password
}
