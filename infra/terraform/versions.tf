terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 6.23.0, < 7.0.0"
    }
  }
}

provider "aws" {
  region  = var.aws_region
  profile = "dealseal-admin"
  default_tags {
    tags = {
      Project     = "CrowdRewards"
      Environment = "hackathon"
      ManagedBy   = "Terraform"
    }
  }
}
