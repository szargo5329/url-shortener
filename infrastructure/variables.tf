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

# App config values that aren't derived from an AWS resource. Defaults match
# application.yml; override at deploy time (e.g. the real domain).
variable "base_short_url" {
  description = "Base URL for generated short links (BASE_SHORT_URL)."
  type        = string
  default     = "https://myapp.io"
}

variable "link_expiration_days" {
  description = "Days until a short link expires (LINK_EXPIRATION_DAYS); fixed at 7 for MVP."
  type        = number
  default     = 7
}

# No default (Section 20.4) — personal, must be supplied via terraform.tfvars
# (gitignored) or an environment variable, never committed.
variable "alert_email" {
  description = "Email address to receive CloudWatch alarm notifications via SNS."
  type        = string
}
