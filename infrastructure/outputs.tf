# Terraform output values for the url-shortener infrastructure.

output "url_mappings_table_name" {
  description = "Name of the DynamoDB table storing short-code → URL mappings."
  value       = aws_dynamodb_table.url_mappings.name
}

output "click_events_table_name" {
  description = "Name of the DynamoDB table storing click events for analytics."
  value       = aws_dynamodb_table.click_events.name
}

output "click_events_queue_url" {
  description = "URL of the SQS click-events queue (the app's SQS_QUEUE_URL)."
  value       = aws_sqs_queue.click_events.url
}

output "click_events_queue_arn" {
  description = "ARN of the SQS click-events queue."
  value       = aws_sqs_queue.click_events.arn
}

output "vpc_id" {
  description = "ID of the application VPC."
  value       = aws_vpc.main.id
}

output "private_subnet_ids" {
  description = "IDs of the two private subnets (AZs a and b)."
  value       = [aws_subnet.private_a.id, aws_subnet.private_b.id]
}

output "redis_primary_endpoint" {
  description = "ElastiCache primary (write) endpoint — what REDIS_HOST is set to."
  value       = aws_elasticache_replication_group.main.primary_endpoint_address
}

output "redis_reader_endpoint" {
  description = "ElastiCache reader endpoint (distributes reads across replicas)."
  value       = aws_elasticache_replication_group.main.reader_endpoint_address
}

output "shorten_function_name" {
  description = "Name of the shorten Lambda function."
  value       = aws_lambda_function.shorten.function_name
}

output "shorten_function_arn" {
  description = "ARN of the shorten Lambda function."
  value       = aws_lambda_function.shorten.arn
}

output "redirect_function_name" {
  description = "Name of the redirect Lambda function."
  value       = aws_lambda_function.redirect.function_name
}

output "redirect_function_arn" {
  description = "ARN of the redirect Lambda function."
  value       = aws_lambda_function.redirect.arn
}

output "analytics_function_name" {
  description = "Name of the analytics Lambda function."
  value       = aws_lambda_function.analytics.function_name
}

output "analytics_function_arn" {
  description = "ARN of the analytics Lambda function."
  value       = aws_lambda_function.analytics.arn
}

output "api_gateway_invoke_url" {
  description = "Base invoke URL of the HTTP API (shorten + redirect routes)."
  value       = aws_apigatewayv2_stage.default.invoke_url
}

output "frontend_bucket_name" {
  description = "Name of the S3 bucket hosting the frontend static files."
  value       = aws_s3_bucket.frontend.bucket
}

output "frontend_bucket_arn" {
  description = "ARN of the frontend S3 bucket."
  value       = aws_s3_bucket.frontend.arn
}

output "cloudfront_domain_name" {
  description = "CloudFront distribution domain name (e.g. dxxxx.cloudfront.net)."
  value       = aws_cloudfront_distribution.frontend.domain_name
}

output "cloudfront_distribution_id" {
  description = "CloudFront distribution ID (for cache invalidations in CI/CD)."
  value       = aws_cloudfront_distribution.frontend.id
}

output "sns_alerts_topic_arn" {
  description = "ARN of the SNS topic that CloudWatch alarms notify."
  value       = aws_sns_topic.alerts.arn
}
