output "password" {
  value = "${local.pg_password}"
  sensitive = true
}

output "endpoint" {
  value = aws_rds_cluster_instance.aurora.endpoint
}

output "database_name" {
  value = aws_rds_cluster.aurora.database_name
}

output "port" {
  value = aws_rds_cluster.aurora.port
}

output "user" {
  value = aws_rds_cluster.aurora.master_username
}
