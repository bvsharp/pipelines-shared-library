provider "aws" {
  region = var.aws_region
}

# Rancher variables
variable "rancher_server_url" {
  type        = string
  default     = "https://rancher.ci.folio.org/v3"
  description = "Rancher server URL"
}

variable "rancher_token_key" {
  type        = string
  description = "Rancher token key"
}

provider "rancher2" {
  api_url   = var.rancher_server_url
  token_key = var.rancher_token_key
}



