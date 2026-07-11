# VPC and networking (Section 6). The ONLY reason a VPC exists in this
# architecture is that ElastiCache requires one. Traffic never reaches the
# public internet from inside the VPC: the Internet Gateway is attached but
# intentionally has NO route pointing at it, so only internal AWS traffic
# (redirect Lambda -> ElastiCache) flows across the private subnets.

resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "url-shortener-vpc"
  }
}

# Two private subnets across two AZs (Multi-AZ) so ElastiCache can place its
# primary and replica in separate AZs. AZ names derive from the region variable
# (us-east-1a / us-east-1b for the default region), per Section 6.
resource "aws_subnet" "private_a" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.1.0/24"
  availability_zone = "${var.aws_region}a"

  tags = {
    Name = "url-shortener-private-a"
  }
}

resource "aws_subnet" "private_b" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.2.0/24"
  availability_zone = "${var.aws_region}b"

  tags = {
    Name = "url-shortener-private-b"
  }
}

# Internet Gateway — PRESENT BUT UNUSED by design (Section 6). It is attached to
# the VPC, but no route table sends 0.0.0.0/0 to it, so nothing can actually
# reach the internet through it. The subnets use the VPC's default (main) route
# table, which carries only the implicit local route.
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "url-shortener-igw"
  }
}

# --- Security groups (Section 16.8) ----------------------------------------
# The two SGs reference each other, so they are created empty and the actual
# rules live in separate rule resources below. Defining the rules inline would
# create a circular dependency Terraform cannot resolve.

# Attached to the ElastiCache Redis nodes.
resource "aws_security_group" "elasticache" {
  name        = "url-shortener-elasticache"
  description = "ElastiCache Redis - inbound 6379 from the redirect Lambda only"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "url-shortener-elasticache"
  }
}

# Attached to the VPC-connected redirect Lambda.
resource "aws_security_group" "redirect_lambda" {
  name        = "url-shortener-redirect-lambda"
  description = "Redirect Lambda - outbound 6379 to ElastiCache only"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "url-shortener-redirect-lambda"
  }
}

# Inbound to ElastiCache: allow Redis (6379) ONLY from the redirect Lambda's SG.
# The source is a security-group reference, not a CIDR — nothing else can connect.
resource "aws_vpc_security_group_ingress_rule" "elasticache_from_redirect" {
  security_group_id            = aws_security_group.elasticache.id
  referenced_security_group_id = aws_security_group.redirect_lambda.id
  from_port                    = 6379
  to_port                      = 6379
  ip_protocol                  = "tcp"
  description                  = "Redis from redirect Lambda"
}

# Outbound from the redirect Lambda: allow Redis (6379) ONLY to ElastiCache's SG.
resource "aws_vpc_security_group_egress_rule" "redirect_to_elasticache" {
  security_group_id            = aws_security_group.redirect_lambda.id
  referenced_security_group_id = aws_security_group.elasticache.id
  from_port                    = 6379
  to_port                      = 6379
  ip_protocol                  = "tcp"
  description                  = "Redis to ElastiCache"
}

# --- VPC Endpoints (Section 6, VPC-attached redirect Lambda gap fix) --------
# The redirect Lambda runs inside the VPC with egress locked down, so its calls
# to DynamoDB (cache miss) and SQS (fire-and-forget analytics) would otherwise be
# blocked. VPC Endpoints give private, in-VPC reachability to those AWS services
# without a NAT Gateway (no ongoing NAT cost, no public internet path).

# DynamoDB — GATEWAY endpoint: free, no ENI. It works by injecting a route to
# DynamoDB's managed prefix list into the VPC route table, so it is associated
# with a route table rather than subnets/security groups.
resource "aws_vpc_endpoint" "dynamodb" {
  vpc_id            = aws_vpc.main.id
  service_name      = "com.amazonaws.${var.aws_region}.dynamodb"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_vpc.main.main_route_table_id]

  tags = {
    Name = "url-shortener-dynamodb-endpoint"
  }
}

# SQS interface endpoint's own security group: allow HTTPS (443) inbound ONLY
# from the redirect Lambda's SG — same least-privilege pattern as ElastiCache.
resource "aws_security_group" "sqs_endpoint" {
  name        = "url-shortener-sqs-endpoint"
  description = "SQS interface endpoint - inbound 443 from the redirect Lambda only"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "url-shortener-sqs-endpoint"
  }
}

resource "aws_vpc_security_group_ingress_rule" "sqs_endpoint_from_redirect" {
  security_group_id            = aws_security_group.sqs_endpoint.id
  referenced_security_group_id = aws_security_group.redirect_lambda.id
  from_port                    = 443
  to_port                      = 443
  ip_protocol                  = "tcp"
  description                  = "HTTPS from redirect Lambda"
}

# SQS — INTERFACE endpoint: creates ENIs in both private subnets (hourly cost),
# reached over HTTPS. private_dns_enabled routes the SDK's normal sqs.<region>
# hostname to these private ENIs.
resource "aws_vpc_endpoint" "sqs" {
  vpc_id              = aws_vpc.main.id
  service_name        = "com.amazonaws.${var.aws_region}.sqs"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = [aws_subnet.private_a.id, aws_subnet.private_b.id]
  security_group_ids  = [aws_security_group.sqs_endpoint.id]
  private_dns_enabled = true

  tags = {
    Name = "url-shortener-sqs-endpoint"
  }
}

# Redirect Lambda additionally needs outbound 443 to the SQS interface endpoint
# to publish click events.
resource "aws_vpc_security_group_egress_rule" "redirect_to_sqs_endpoint" {
  security_group_id            = aws_security_group.redirect_lambda.id
  referenced_security_group_id = aws_security_group.sqs_endpoint.id
  from_port                    = 443
  to_port                      = 443
  ip_protocol                  = "tcp"
  description                  = "HTTPS to SQS endpoint"
}

# Redirect Lambda also needs outbound 443 to DynamoDB on cache miss. The gateway
# endpoint has no security group, so egress is scoped to DynamoDB's managed prefix
# list (exposed by the endpoint) rather than an SG reference.
resource "aws_vpc_security_group_egress_rule" "redirect_to_dynamodb" {
  security_group_id = aws_security_group.redirect_lambda.id
  prefix_list_id    = aws_vpc_endpoint.dynamodb.prefix_list_id
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
  description       = "HTTPS to DynamoDB gateway endpoint"
}
