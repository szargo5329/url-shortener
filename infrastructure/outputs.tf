# Terraform output values for the url-shortener infrastructure.
#
# More outputs will be added as resources are created in Step 12b, such as:
#   - API Gateway invoke URL
#   - CloudFront distribution domain name
#   - ElastiCache primary endpoint
#   - SQS queue URL
#   - S3 frontend bucket name

output "url_mappings_table_name" {
  description = "Name of the DynamoDB table storing short-code → URL mappings."
  value       = aws_dynamodb_table.url_mappings.name
}

output "click_events_table_name" {
  description = "Name of the DynamoDB table storing click events for analytics."
  value       = aws_dynamodb_table.click_events.name
}
