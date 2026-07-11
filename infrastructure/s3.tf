# S3 bucket for the frontend static files (Section 6). This is a PRIVATE origin
# for CloudFront (via Origin Access Control), so it must never be directly
# publicly reachable: S3 "static website hosting" is intentionally NOT enabled,
# and all public access is blocked below. The bucket policy that grants
# CloudFront read access lives in cloudfront.tf — it needs the distribution ARN,
# which does not exist yet.

data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "frontend" {
  # Bucket names are globally unique; the account ID suffix avoids collisions.
  bucket = "url-shortener-frontend-${data.aws_caller_identity.current.account_id}"

  tags = {
    Name = "url-shortener-frontend"
  }
}

# Block ALL public access — the bucket is reachable only through CloudFront.
resource "aws_s3_bucket_public_access_block" "frontend" {
  bucket = aws_s3_bucket.frontend.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Keep prior versions of frontend assets (safe rollbacks of a bad deploy).
resource "aws_s3_bucket_versioning" "frontend" {
  bucket = aws_s3_bucket.frontend.id

  versioning_configuration {
    status = "Enabled"
  }
}
