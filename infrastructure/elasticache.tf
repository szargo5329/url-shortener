# ElastiCache (Redis) for the cache-aside hot-code cache (Section 6).
# Multi-AZ replication group: one primary in AZ a, one replica in AZ b, with
# automatic failover. Lives in the private subnets and is reachable only from
# the redirect Lambda via the elasticache security group defined in vpc.tf.

# Tells ElastiCache which subnets (and therefore which AZs) it may place nodes in.
resource "aws_elasticache_subnet_group" "main" {
  name       = "url-shortener-cache-subnets"
  subnet_ids = [aws_subnet.private_a.id, aws_subnet.private_b.id]

  tags = {
    Name = "url-shortener-cache-subnets"
  }
}

resource "aws_elasticache_replication_group" "main" {
  replication_group_id = "url-shortener-redis"
  description          = "Redis cache for hot short codes (cache-aside)"

  engine         = "redis"
  engine_version = "7.1"
  # Smallest current-gen (Graviton) node — cost-appropriate for ~10 users, not a
  # large production instance.
  node_type = "cache.t4g.micro"
  port      = 6379

  # Two nodes = one primary + one replica, one per AZ, with auto-failover so the
  # replica is promoted if the primary's AZ fails. multi_az_enabled requires
  # automatic_failover_enabled.
  num_cache_clusters          = 2
  automatic_failover_enabled  = true
  multi_az_enabled            = true
  preferred_cache_cluster_azs = ["${var.aws_region}a", "${var.aws_region}b"]

  subnet_group_name  = aws_elasticache_subnet_group.main.name
  security_group_ids = [aws_security_group.elasticache.id]

  # Encrypt data at rest (client-transparent). In-transit/TLS is intentionally
  # left off: it would require reworking the tested RedisConfig.java to use TLS,
  # and this traffic never leaves the private VPC.
  at_rest_encryption_enabled = true

  tags = {
    Name = "url-shortener-redis"
  }
}
