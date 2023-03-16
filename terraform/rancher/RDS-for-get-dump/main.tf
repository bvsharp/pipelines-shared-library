data "aws_db_cluster_snapshot" "latest_prod_snapshot" {
  db_cluster_snapshot_identifier = var.arn_db_snapshot
}

resource "aws_rds_cluster" "aurora" {
  cluster_identifier   = "temporary-rds-cluster-destroy-after-4-hours"
  snapshot_identifier  = data.aws_db_cluster_snapshot.latest_prod_snapshot.id
  db_subnet_group_name = aws_db_subnet_group.default.name
  engine               = data.aws_db_cluster_snapshot.latest_prod_snapshot.engine
  engine_version       = data.aws_db_cluster_snapshot.latest_prod_snapshot.engine_version
  master_username      = "folio"
  master_password      = local.pg_password
  skip_final_snapshot  = true
  database_name = "testDB"
}

resource "aws_rds_cluster_instance" "aurora" {
  cluster_identifier   = aws_rds_cluster.aurora.id
  instance_class       = var.asg_instance_types
  db_subnet_group_name = aws_db_subnet_group.default.name
  engine               = data.aws_db_cluster_snapshot.latest_prod_snapshot.engine
  engine_version       = data.aws_db_cluster_snapshot.latest_prod_snapshot.engine_version
}

resource "aws_db_subnet_group" "default" {
  name       = "temporary-should-destroy-in-4-hours"
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
  override_special = "@$%-+=?"
}

locals {
  pg_password = var.pg_password == "" ? random_password.pg_password.result : var.pg_password
}
