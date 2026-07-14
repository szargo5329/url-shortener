# Lambda functions (Section 6 "Lambda Handler Wiring").
#
# - shorten + redirect share ONE handler class (StreamLambdaHandler) but exist as
#   two separate functions because VPC attachment is per-function: only redirect
#   is VPC-attached (for ElastiCache); shorten stays out of the VPC.
# - analytics is SQS-triggered and uses AnalyticsLambdaHandler.
#
# No real artifact exists yet (CI/CD deploy is Step 13), so every function is
# created with a trivial placeholder zip. A lifecycle ignore_changes block keeps
# a later `terraform apply` from reverting the real code a deploy uploads.

# Trivial placeholder deployment package. Real JARs are shipped by CI/CD later.
data "archive_file" "lambda_placeholder" {
  type        = "zip"
  output_path = "${path.module}/build/lambda-placeholder.zip"

  source {
    content  = "placeholder - the real Lambda artifact is deployed via CI/CD (Step 13)"
    filename = "placeholder.txt"
  }
}

# Full environment variable set, identical across ALL three functions. Every
# function boots the entire Spring context, so every referenced property must
# resolve at startup even if that function never functionally uses it (Section 6,
# gap 2). Values come from real resources; nothing domain-specific is hardcoded.
locals {
  lambda_environment = {
    DYNAMODB_TABLE_NAME              = aws_dynamodb_table.url_mappings.name
    DYNAMODB_CLICK_EVENTS_TABLE_NAME = aws_dynamodb_table.click_events.name
    REDIS_HOST                       = aws_elasticache_replication_group.main.primary_endpoint_address
    REDIS_PORT                       = "6379"
    SQS_QUEUE_URL                    = aws_sqs_queue.click_events.url
    BASE_SHORT_URL                   = var.base_short_url
    FRONTEND_ORIGIN                  = "https://${aws_cloudfront_distribution.frontend.domain_name}"
    LINK_EXPIRATION_DAYS             = tostring(var.link_expiration_days)
  }
}

# λ shorten — HTTP POST /shorten. Not VPC-attached (avoids VPC cold-start cost).
resource "aws_lambda_function" "shorten" {
  function_name = "url-shortener-shorten"
  role          = aws_iam_role.shorten_lambda_role.arn
  runtime       = "java21"
  handler       = "com.urlshortener.handler.StreamLambdaHandler::handleRequest"
  memory_size   = 512
  timeout       = 30

  filename         = data.archive_file.lambda_placeholder.output_path
  source_code_hash = data.archive_file.lambda_placeholder.output_base64sha256

  environment {
    variables = local.lambda_environment
  }

  lifecycle {
    ignore_changes = [filename, source_code_hash]
  }

  tags = {
    Name = "url-shortener-shorten"
  }
}

# λ redirect — HTTP GET /{code}. VPC-attached so it can reach ElastiCache.
resource "aws_lambda_function" "redirect" {
  function_name = "url-shortener-redirect"
  role          = aws_iam_role.redirect_lambda_role.arn
  runtime       = "java21"
  handler       = "com.urlshortener.handler.StreamLambdaHandler::handleRequest"
  memory_size   = 512
  timeout       = 30

  filename         = data.archive_file.lambda_placeholder.output_path
  source_code_hash = data.archive_file.lambda_placeholder.output_base64sha256

  environment {
    variables = local.lambda_environment
  }

  vpc_config {
    subnet_ids         = [aws_subnet.private_a.id, aws_subnet.private_b.id]
    security_group_ids = [aws_security_group.redirect_lambda.id]
  }

  lifecycle {
    ignore_changes = [filename, source_code_hash]
  }

  tags = {
    Name = "url-shortener-redirect"
  }
}

# λ analytics — SQS-triggered. Runs outside the VPC.
resource "aws_lambda_function" "analytics" {
  function_name = "url-shortener-analytics"
  role          = aws_iam_role.analytics_lambda_role.arn
  runtime       = "java21"
  handler       = "com.urlshortener.handler.AnalyticsLambdaHandler::handleRequest"
  memory_size   = 512
  timeout       = 30

  filename         = data.archive_file.lambda_placeholder.output_path
  source_code_hash = data.archive_file.lambda_placeholder.output_base64sha256

  environment {
    variables = local.lambda_environment
  }

  lifecycle {
    ignore_changes = [filename, source_code_hash]
  }

  tags = {
    Name = "url-shortener-analytics"
  }
}

# Wires the click-events SQS queue to the analytics function so AWS invokes it
# with batches of messages as they arrive.
resource "aws_lambda_event_source_mapping" "analytics_sqs" {
  event_source_arn = aws_sqs_queue.click_events.arn
  function_name    = aws_lambda_function.analytics.arn
}
