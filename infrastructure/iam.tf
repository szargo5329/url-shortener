# IAM roles and least-privilege policies, one execution role per Lambda
# (Section 16.5). Each role gets only the exact actions its function needs —
# no dynamodb:* wildcards, no AdministratorAccess. DynamoDB table ARNs are
# referenced from the aws_dynamodb_table resources (dynamodb.tf) rather than
# hardcoded, and the click-events queue ARN from aws_sqs_queue.click_events
# (sqs.tf), so nothing is hardcoded.
#
# ── How to read this file (IAM in a nutshell) ──────────────────────────────
# Two policy concepts, kept deliberately distinct:
#   * TRUST policy      — WHO may assume a role (here: the Lambda service).
#   * PERMISSION policy — WHAT an assumed role may do (read DynamoDB, send SQS…).
# Three mechanical kinds of block appear below:
#   * data "aws_iam_policy_document" — just BUILDS JSON text; creates nothing.
#   * resource "aws_iam_role"        — creates an empty identity (name + trust).
#   * granting access happens two ways:
#       - attachment (aws_iam_role_policy_attachment) -> point the role at an
#         existing AWS-managed policy.
#       - inline (aws_iam_role_policy) -> embed our own JSON onto one role.
# ───────────────────────────────────────────────────────────────────────────

# TRUST POLICY (who may *assume* the role) — this is NOT a permissions policy and
# grants no access to any service. It only says the Lambda service may assume these
# roles. Referenced by all three roles' assume_role_policy below.
# Fundamentally: this just *builds a JSON document* — a data source creates nothing
# in AWS on its own; the JSON isn't used until a role references it.
data "aws_iam_policy_document" "lambda_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

# AWS-managed permission-policy ARNs, kept in locals so each ARN is written once
# and reused by the attachments below (DRY).
# Fundamentally: just two named string constants (labels for ARNs). Creates nothing.
locals {
  lambda_basic_execution_policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
  lambda_vpc_access_policy_arn      = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

# ---------------------------------------------------------------------------
# λ shorten — GetItem + PutItem on url-mappings
# ---------------------------------------------------------------------------

# The shorten Lambda's identity. A role by itself grants nothing: it pairs a trust
# policy (assume_role_policy, above) with the permission policies attached below.
# Fundamentally: this creates one IAM role in AWS — a name plus its trust policy.
# No permissions are attached here.
resource "aws_iam_role" "shorten_lambda_role" {
  name               = "url-shortener-shorten-lambda"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

# MANAGED-policy ATTACHMENT (not inline): grants CloudWatch Logs access. Every Lambda
# needs this or it can't write logs at all. Managed because AWS maintains it for us.
# Fundamentally: it's attaching an existing AWS-managed policy to shorten_lambda_role,
# nothing more.
resource "aws_iam_role_policy_attachment" "shorten_lambda_basic_execution" {
  role       = aws_iam_role.shorten_lambda_role.name
  policy_arn = local.lambda_basic_execution_policy_arn
}

# Builds the JSON for shorten's custom PERMISSION policy. GetItem is required for the
# pre-save collision check; PutItem stores the new short-code mapping.
# Fundamentally: another JSON-builder data source — produces policy text, attaches
# nothing on its own.
data "aws_iam_policy_document" "shorten_dynamodb" {
  statement {
    sid    = "UrlMappingsReadWrite"
    effect = "Allow"
    actions = [
      "dynamodb:GetItem", # collision check via ShortCodeGenerator.generateUnique
      "dynamodb:PutItem",
    ]
    resources = [aws_dynamodb_table.url_mappings.arn]
  }
}

# INLINE policy (aws_iam_role_policy): lives on this one role. Inline rather than a
# reusable managed policy because it's a one-off grant used by nothing else.
# Fundamentally: this writes that JSON straight onto the role as an inline policy —
# the grant exists only on shorten_lambda_role.
resource "aws_iam_role_policy" "shorten_dynamodb" {
  name   = "url-shortener-shorten-dynamodb"
  role   = aws_iam_role.shorten_lambda_role.id
  policy = data.aws_iam_policy_document.shorten_dynamodb.json
}

# ---------------------------------------------------------------------------
# λ redirect — GetItem on url-mappings, SendMessage to click-events queue,
# VPC networking (it is the only VPC-attached function)
# ---------------------------------------------------------------------------

# The redirect Lambda's identity — same Lambda trust policy as the other roles.
# Fundamentally: creates the redirect IAM role — name + trust policy only, no permissions yet.
# creates the redirect IAM role, names it 
resource "aws_iam_role" "redirect_lambda_role" {
  name               = "url-shortener-redirect-lambda"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

# MANAGED-policy attachment: CloudWatch Logs access, the same baseline every role gets.
# Fundamentally: attaching the existing managed logging policy to redirect_lambda_role,
# nothing more.
resource "aws_iam_role_policy_attachment" "redirect_lambda_basic_execution" {
  role       = aws_iam_role.redirect_lambda_role.name
  policy_arn = local.lambda_basic_execution_policy_arn
}

# MANAGED-policy attachment, redirect ONLY: lets the function create/delete the elastic
# network interfaces (ENIs) it needs to run inside the VPC and reach ElastiCache/Redis.
# Fundamentally: attaching a *second* existing managed policy to redirect_lambda_role,
# nothing more.
resource "aws_iam_role_policy_attachment" "redirect_lambda_vpc_access" {
  role       = aws_iam_role.redirect_lambda_role.name
  policy_arn = local.lambda_vpc_access_policy_arn
}

# JSON for redirect's custom PERMISSION policy: read one mapping from DynamoDB, and
# publish a click event to the SQS queue (fire-and-forget analytics).
# Fundamentally: JSON-builder only — two statements of allowed actions; creates no AWS resource.
data "aws_iam_policy_document" "redirect_policy" {
  statement {
    sid       = "UrlMappingsRead"
    effect    = "Allow"
    actions   = ["dynamodb:GetItem"]
    resources = [aws_dynamodb_table.url_mappings.arn]
  }

  statement {
    sid       = "ClickEventsPublish"
    effect    = "Allow"
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.click_events.arn]
  }
}

# INLINE policy holding the two custom grants above, scoped to the redirect role only.
# Fundamentally: embeds that JSON into redirect_lambda_role as an inline policy.
resource "aws_iam_role_policy" "redirect_policy" {
  name   = "url-shortener-redirect-access"
  role   = aws_iam_role.redirect_lambda_role.id
  policy = data.aws_iam_policy_document.redirect_policy.json
}

# ---------------------------------------------------------------------------
# λ analytics — PutItem on click-events, consume from the click-events queue
# ---------------------------------------------------------------------------

# The analytics Lambda's identity — same Lambda trust policy as the others.
# Fundamentally: creates the analytics IAM role — name + trust policy only.
resource "aws_iam_role" "analytics_lambda_role" {
  name               = "url-shortener-analytics-lambda"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

# MANAGED-policy attachment: CloudWatch Logs access.
# Fundamentally: attaching the existing managed logging policy to analytics_lambda_role,
# nothing more.
resource "aws_iam_role_policy_attachment" "analytics_lambda_basic_execution" {
  role       = aws_iam_role.analytics_lambda_role.name
  policy_arn = local.lambda_basic_execution_policy_arn
}

# JSON for analytics' custom PERMISSION policy: write click records to click-events,
# and receive/delete/inspect messages on the SQS queue it consumes from.
# Fundamentally: JSON-builder only — the analytics grants as text; nothing created.
data "aws_iam_policy_document" "analytics_policy" {
  statement {
    sid       = "ClickEventsWrite"
    effect    = "Allow"
    actions   = ["dynamodb:PutItem"]
    resources = [aws_dynamodb_table.click_events.arn]
  }

  statement {
    sid    = "ClickEventsConsume"
    effect = "Allow"
    actions = [
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:GetQueueAttributes",
    ]
    resources = [aws_sqs_queue.click_events.arn]
  }
}

# INLINE policy: the DynamoDB-write + SQS-consume grants above, analytics role only.
# Fundamentally: embeds that JSON into analytics_lambda_role as an inline policy.
resource "aws_iam_role_policy" "analytics_policy" {
  name   = "url-shortener-analytics-access"
  role   = aws_iam_role.analytics_lambda_role.id
  policy = data.aws_iam_policy_document.analytics_policy.json
}
