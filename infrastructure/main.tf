terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    # Used to build a trivial placeholder Lambda deployment package until the
    # CI/CD pipeline (Step 13) ships the real JAR.
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.0"
    }
    # Used to fetch GitHub's OIDC TLS thumbprint dynamically (see ci_cd.tf).
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  # Remote state (Section 19.5) — intentionally commented out for now. Local
  # state is fine for initial learning/testing. Once Terraform basics are
  # comfortable, migrate to an S3 backend with a DynamoDB lock table (the
  # standard AWS-native approach) to prevent state loss and allow safe
  # concurrent access when CI/CD runs `terraform apply`.
  #
  # backend "s3" {
  #   bucket         = "url-shortener-terraform-state"
  #   key            = "infrastructure/terraform.tfstate"
  #   region         = "us-east-1"
  #   dynamodb_table = "url-shortener-terraform-locks"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.aws_region

  # Applied to every resource that supports tagging — no need to repeat per resource.
  default_tags {
    tags = {
      Project     = "url-shortener"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}
