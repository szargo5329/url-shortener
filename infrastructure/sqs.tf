# SQS queue for click events (Section 17). The redirect Lambda publishes a
# fire-and-forget message here on every successful redirect; the analytics
# Lambda consumes them and writes to the click-events DynamoDB table.
#
# Standard queue (not FIFO): click analytics tolerate best-effort ordering and
# the rare duplicate, and standard queues offer far higher throughput.
resource "aws_sqs_queue" "click_events" {
  name = "click-events"
}
