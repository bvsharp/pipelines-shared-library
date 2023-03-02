# Getting the cluster data from the variable rancher_cluster_name.
data "rancher2_cluster" "this" {
  name = var.rancher_cluster_name
}

# Getting the EKS cluster data from the Rancher cluster name.
data "aws_eks_cluster" "this" {
  name = data.rancher2_cluster.this.name
}

data "aws_eks_cluster_auth" "this" {
  name = data.rancher2_cluster.this.name
}

# Getting the subnets that are tagged with "database" and are in the VPC that the EKS cluster is in.
data "aws_subnets" "database" {
  filter {
    name   = "vpc-id"
    values = [data.aws_eks_cluster.this.vpc_config[0].vpc_id]
  }

  tags = {
    Type = "database"
  }
}
