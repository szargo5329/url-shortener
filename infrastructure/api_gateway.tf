# API Gateway — HTTP API (v2) fronting the shorten and redirect Lambdas
# (Section 3). HTTP API is chosen over REST API for its simplicity and lower
# cost for just two routes.
resource "aws_apigatewayv2_api" "main" {
  name          = "url-shortener-api"
  protocol_type = "HTTP"
}

# --- Integrations -----------------------------------------------------------
# CRITICAL: payload_format_version = "1.0" (Section 6, gap 3). StreamLambdaHandler
# uses getAwsProxyHandler(...), which expects the REST-API-v1-shaped proxy event.
# HTTP API's DEFAULT "2.0" payload is a different shape the handler cannot parse,
# so every request would fail. Forcing "1.0" makes HTTP API emit the v1 event.

resource "aws_apigatewayv2_integration" "shorten" {
  api_id                 = aws_apigatewayv2_api.main.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.shorten.invoke_arn
  payload_format_version = "1.0"
}

resource "aws_apigatewayv2_integration" "redirect" {
  api_id                 = aws_apigatewayv2_api.main.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.redirect.invoke_arn
  payload_format_version = "1.0"
}

# --- Routes (Section 3) -----------------------------------------------------
resource "aws_apigatewayv2_route" "shorten" {
  api_id    = aws_apigatewayv2_api.main.id
  route_key = "POST /shorten"
  target    = "integrations/${aws_apigatewayv2_integration.shorten.id}"
}

resource "aws_apigatewayv2_route" "redirect" {
  api_id    = aws_apigatewayv2_api.main.id
  route_key = "GET /{code}"
  target    = "integrations/${aws_apigatewayv2_integration.redirect.id}"
}

# --- Stage + throttling (Section 16.1) --------------------------------------
# CORS is intentionally NOT configured here — UrlController's tested @CrossOrigin
# handles CORS entirely in Spring (Section 16.3). Doing it in both layers risks
# conflicting behavior.
resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.main.id
  name        = "$default"
  auto_deploy = true

  default_route_settings {
    throttling_rate_limit  = 50
    throttling_burst_limit = 100
  }

  # Creation is more expensive than a redirect, so POST /shorten gets a tighter
  # per-route rate limit. Reference the route's route_key (not the literal
  # string) so Terraform knows the stage depends on the route existing first.
  route_settings {
    route_key              = aws_apigatewayv2_route.shorten.route_key
    throttling_rate_limit  = 10
    throttling_burst_limit = 20
  }
}

# --- Lambda invoke permissions ----------------------------------------------
# Allow THIS API (scoped to its execution ARN) to invoke each function. Without
# these, API Gateway calls to the Lambdas are denied.
resource "aws_lambda_permission" "apigw_shorten" {
  statement_id  = "AllowApiGatewayInvokeShorten"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.shorten.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.main.execution_arn}/*/*"
}

resource "aws_lambda_permission" "apigw_redirect" {
  statement_id  = "AllowApiGatewayInvokeRedirect"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.redirect.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.main.execution_arn}/*/*"
}
