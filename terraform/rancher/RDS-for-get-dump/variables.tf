variable "aws_region" {
  type        = string
  default     = "us-west-2"
  description = "Rancher AWS region for S3 buckets"
}

variable "rancher_cluster_name" {
  type        = string
  default =   "folio-dev"
  description = "Rancher cluster name"
}

variable "aws_access_key_id" {
  type        = string
  description = "AWS Access Key ID"
}

variable "aws_secret_access_key" {
  type        = string
  description = "AWS Secret Access Key"
}

variable "asg_instance_types" {
  type        = string
  default     = "db.r5.xlarge"
  description = "List of EC2 instance machine types to be used in EKS."
}

variable "tags" {
  type = map(any)
  default = {
    Terraform = "true"
    Project   = "folio"
    Team      = "kitfox"
  }
  description = "Default tags"
}

variable "pg_password" {
  type        = string
  description = "Postgres password"
}

variable "arn_db_snapshot" {
  type        = string
  description = ""
}
