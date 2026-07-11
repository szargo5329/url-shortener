# Terraform output values for the url-shortener infrastructure.
#
# More outputs will be added as resources are created in Step 12b, such as:
#   - API Gateway invoke URL
#   - CloudFront distribution domain name
#   - S3 frontend bucket name

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
