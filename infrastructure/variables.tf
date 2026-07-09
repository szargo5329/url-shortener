variable "aws_region" {
  description = "AWS region in which all infrastructure resources are created."
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Deployment environment name (e.g. dev, prod). Used for tagging and resource naming."
  type        = string
  default     = "dev"
}
