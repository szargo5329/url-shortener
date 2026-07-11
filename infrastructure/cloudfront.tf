# CloudFront distribution fronting the private frontend S3 bucket (Section 6).
# Access to S3 is via Origin Access Control (OAC) — the bucket stays private and
# only CloudFront can read it (see the bucket policy in s3.tf).

# OAC signs CloudFront's requests to S3 with SigV4 so S3 can authorize them.
resource "aws_cloudfront_origin_access_control" "frontend" {
  name                              = "url-shortener-frontend-oac"
  description                       = "OAC for the frontend S3 origin"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# AWS-managed cache policy — preferred over legacy forwarded_values.
data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}

resource "aws_cloudfront_distribution" "frontend" {
  enabled             = true
  default_root_object = "index.html"
  price_class         = "PriceClass_100" # cost-appropriate for this project's scale

  # NOTE: no WAF (web_acl_id) association. Intentionally out of scope for MVP —
  # it carries ongoing cost and overlaps with API Gateway's existing throttling
  # (Section 16.1). Documented as a future hardening upgrade.

  origin {
    domain_name              = aws_s3_bucket.frontend.bucket_regional_domain_name
    origin_id                = "s3-frontend"
    origin_access_control_id = aws_cloudfront_origin_access_control.frontend.id
  }

  default_cache_behavior {
    target_origin_id       = "s3-frontend"
    viewer_protocol_policy = "redirect-to-https" # Section 16.4 — force HTTPS
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = data.aws_cloudfront_cache_policy.caching_optimized.id
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # Default CloudFront certificate (*.cloudfront.net) — no custom domain yet.
  viewer_certificate {
    cloudfront_default_certificate = true
  }

  tags = {
    Name = "url-shortener-frontend"
  }
}
