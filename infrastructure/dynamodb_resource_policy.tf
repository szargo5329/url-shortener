# DynamoDB resource-based policies (Section 16.5 "DynamoDB resource policy").
# Each table denies data-plane item access to EVERY principal except the specific
# Lambda roles that need it, plus the account root (so root can still view items
# in the console). Deny is deliberately scoped to item operations only — NOT
# table-management actions — so Terraform can keep managing the table and console
# schema browsing (Describe*, etc.) keeps working.
#
# aws_caller_identity.current is already declared in s3.tf, so it is only
# referenced here, not redeclared.

locals {
  # Item-level (data-plane) actions the deny covers. Table-management actions are
  # intentionally excluded so IaC and console schema browsing keep working.
  dynamodb_data_plane_actions = [
    "dynamodb:GetItem",
    "dynamodb:PutItem",
    "dynamodb:UpdateItem",
    "dynamodb:DeleteItem",
    "dynamodb:Query",
    "dynamodb:Scan",
    "dynamodb:BatchGetItem",
    "dynamodb:BatchWriteItem",
  ]

  account_root_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
}

# --- url-mappings: only shorten + redirect roles (and root) may touch items ---
data "aws_iam_policy_document" "url_mappings_resource_policy" {
  statement {
    sid    = "DenyDataAccessExceptAppRoles"
    effect = "Deny"

    principals {
      type        = "AWS"
      identifiers = ["*"]
    }

    actions = local.dynamodb_data_plane_actions

    resources = [
      aws_dynamodb_table.url_mappings.arn,
      "${aws_dynamodb_table.url_mappings.arn}/*",
    ]

    # Deny applies to every principal whose ARN is NOT in this allow-list.
    condition {
      test     = "StringNotEquals"
      variable = "aws:PrincipalArn"
      values = [
        aws_iam_role.shorten_lambda_role.arn,
        aws_iam_role.redirect_lambda_role.arn,
        local.account_root_arn,
      ]
    }
  }
}

resource "aws_dynamodb_resource_policy" "url_mappings" {
  resource_arn = aws_dynamodb_table.url_mappings.arn
  policy       = data.aws_iam_policy_document.url_mappings_resource_policy.json
}

# --- click-events: only the analytics role (and root) may touch items ---------
data "aws_iam_policy_document" "click_events_resource_policy" {
  statement {
    sid    = "DenyDataAccessExceptAppRoles"
    effect = "Deny"

    principals {
      type        = "AWS"
      identifiers = ["*"]
    }

    actions = local.dynamodb_data_plane_actions

    resources = [
      aws_dynamodb_table.click_events.arn,
      "${aws_dynamodb_table.click_events.arn}/*",
    ]

    condition {
      test     = "StringNotEquals"
      variable = "aws:PrincipalArn"
      values = [
        aws_iam_role.analytics_lambda_role.arn,
        local.account_root_arn,
      ]
    }
  }
}

resource "aws_dynamodb_resource_policy" "click_events" {
  resource_arn = aws_dynamodb_table.click_events.arn
  policy       = data.aws_iam_policy_document.click_events_resource_policy.json
}
