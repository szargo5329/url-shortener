# DynamoDB tables (Section 6 and Section 17.4).
#
# Both use on-demand (PAY_PER_REQUEST) billing — no capacity planning needed at
# this scale and cost is effectively zero. Only key attributes are declared here;
# DynamoDB is otherwise schemaless, so non-key attributes (long_url, created_at,
# expires_at, etc.) are written by the application without being defined in HCL.

resource "aws_dynamodb_table" "url_mappings" {
  name         = "url-mappings"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "short_code"

  attribute {
    name = "short_code"
    type = "S"
  }

  # Native DynamoDB TTL intentionally NOT configured yet — decision deferred to
  # this step per Section 15 (pending confirmation before enabling either way).
}

resource "aws_dynamodb_table" "click_events" {
  name         = "click-events"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "short_code"
  range_key    = "clicked_at"

  attribute {
    name = "short_code"
    type = "S"
  }

  attribute {
    name = "clicked_at"
    type = "S"
  }
}
