# CI/CD authentication via GitHub Actions OIDC federation (Section 14).
#
# GitHub Actions assumes an AWS role using a short-lived OIDC token instead of
# long-lived AWS access keys stored as GitHub secrets. The role is locked to this
# repo's main branch and can do exactly one thing: update the three Lambdas' code.

# Fetch GitHub's OIDC endpoint TLS chain so the provider thumbprint is derived at
# apply time. GitHub rotates this certificate periodically, so a hardcoded
# thumbprint would silently go stale — this keeps it current automatically.
data "tls_certificate" "github_actions" {
  url = "https://token.actions.githubusercontent.com"
}

locals {
  # AWS expects the thumbprint of the root CA — the last cert in the chain.
  github_oidc_thumbprint = data.tls_certificate.github_actions.certificates[
    length(data.tls_certificate.github_actions.certificates) - 1
  ].sha1_fingerprint
}

# Registers GitHub Actions as a trusted OIDC identity provider in this account.
resource "aws_iam_openid_connect_provider" "github_actions" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [local.github_oidc_thumbprint]
}

# Trust policy: allow web-identity assumption ONLY from the OIDC provider above,
# and only when the token's audience is sts.amazonaws.com AND its subject is a
# push to main on this specific repo. This is what restricts who can assume the
# role — not an AWS key, but the identity of the GitHub workflow itself.
data "aws_iam_policy_document" "github_actions_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github_actions.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:szargo5329/url-shortener:ref:refs/heads/main"]
    }
  }
}

resource "aws_iam_role" "github_actions_deploy" {
  name               = "url-shortener-github-actions-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_actions_assume_role.json
}

# Least-privilege deploy policy: ONLY lambda:UpdateFunctionCode, and only on the
# three function ARNs. CI/CD must never touch function configuration or env vars
# — those stay Terraform-managed (the lifecycle ignore_changes in lambda.tf keeps
# a code deploy from being reverted, and keeps config out of CI/CD's hands).
data "aws_iam_policy_document" "github_actions_deploy" {
  statement {
    sid     = "UpdateLambdaCode"
    effect  = "Allow"
    actions = ["lambda:UpdateFunctionCode"]
    resources = [
      aws_lambda_function.shorten.arn,
      aws_lambda_function.redirect.arn,
      aws_lambda_function.analytics.arn,
    ]
  }
}

resource "aws_iam_role_policy" "github_actions_deploy" {
  name   = "url-shortener-github-actions-deploy"
  role   = aws_iam_role.github_actions_deploy.id
  policy = data.aws_iam_policy_document.github_actions_deploy.json
}
