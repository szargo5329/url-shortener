# CloudWatch monitoring & alarms (Section 20). A minimal, high-signal set: one
# SNS topic with an email subscription that every alarm notifies, plus alarms
# for the failure modes that actually matter at this scale. CloudWatch Logs and
# baseline metrics are already automatic (Section 20.1) — this only adds alarms.

# SNS topic all alarms notify. One topic + email is sufficient here (no
# PagerDuty/Slack). NOTE: email subscriptions require a one-time manual click on
# the confirmation email AWS sends after apply (Section 20.5).
resource "aws_sns_topic" "alerts" {
  name = "url-shortener-alerts"
}

resource "aws_sns_topic_subscription" "email" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

# --- Lambda error alarms (one per function) ---------------------------------
# Fire if a function records any Errors (sum > 0) in a 5-minute window. Missing
# data = no errors = healthy, so treat it as not breaching.

resource "aws_cloudwatch_metric_alarm" "shorten_lambda_errors" {
  alarm_name          = "url-shortener-shorten-lambda-errors"
  alarm_description   = "Shorten Lambda reported one or more errors."
  namespace           = "AWS/Lambda"
  metric_name         = "Errors"
  dimensions          = { FunctionName = aws_lambda_function.shorten.function_name }
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  comparison_operator = "GreaterThanThreshold"
  threshold           = 0
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "redirect_lambda_errors" {
  alarm_name          = "url-shortener-redirect-lambda-errors"
  alarm_description   = "Redirect Lambda reported one or more errors."
  namespace           = "AWS/Lambda"
  metric_name         = "Errors"
  dimensions          = { FunctionName = aws_lambda_function.redirect.function_name }
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  comparison_operator = "GreaterThanThreshold"
  threshold           = 0
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "analytics_lambda_errors" {
  alarm_name          = "url-shortener-analytics-lambda-errors"
  alarm_description   = "Analytics Lambda reported one or more errors."
  namespace           = "AWS/Lambda"
  metric_name         = "Errors"
  dimensions          = { FunctionName = aws_lambda_function.analytics.function_name }
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  comparison_operator = "GreaterThanThreshold"
  threshold           = 0
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
}

# --- API Gateway 5xx alarm --------------------------------------------------
# This is an HTTP API (v2), whose CloudWatch metric is "5xx" dimensioned by
# ApiId (the REST-API "5XXError"/ApiName names do not apply here).
resource "aws_cloudwatch_metric_alarm" "api_gateway_5xx" {
  alarm_name          = "url-shortener-api-5xx-errors"
  alarm_description   = "API Gateway returned one or more 5xx responses."
  namespace           = "AWS/ApiGateway"
  metric_name         = "5xx"
  dimensions          = { ApiId = aws_apigatewayv2_api.main.id }
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  comparison_operator = "GreaterThanThreshold"
  threshold           = 0
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
}

# --- DynamoDB throttled request alarms (one per table) ----------------------
# Throttling at on-demand billing signals abuse or unexpected load worth knowing.

resource "aws_cloudwatch_metric_alarm" "url_mappings_throttled" {
  alarm_name          = "url-shortener-url-mappings-throttled"
  alarm_description   = "url-mappings table had throttled requests."
  namespace           = "AWS/DynamoDB"
  metric_name         = "ThrottledRequests"
  dimensions          = { TableName = aws_dynamodb_table.url_mappings.name }
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  comparison_operator = "GreaterThanThreshold"
  threshold           = 0
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "click_events_throttled" {
  alarm_name          = "url-shortener-click-events-throttled"
  alarm_description   = "click-events table had throttled requests."
  namespace           = "AWS/DynamoDB"
  metric_name         = "ThrottledRequests"
  dimensions          = { TableName = aws_dynamodb_table.click_events.name }
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  comparison_operator = "GreaterThanThreshold"
  threshold           = 0
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
}
