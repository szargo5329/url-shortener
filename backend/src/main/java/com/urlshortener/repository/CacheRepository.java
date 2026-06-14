package com.urlshortener.repository;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

/**
 * Redis (ElastiCache) cache for short-code → long-URL lookups, backing the
 * cache-aside pattern in the redirect path.
 *
 * <p>Entries are written with a 24-hour TTL so stale or dead links do not
 * linger in memory; once Redis evicts a key the next lookup is a cache miss
 * that falls through to DynamoDB and repopulates the cache.
 */
@Repository
public class CacheRepository {

    /** TTL applied to every cache entry per the spec's caching strategy. */
    @NonNull
    private static final Duration TTL = Objects.requireNonNull(Duration.ofHours(24));

    private final RedisTemplate<String, String> redisTemplate;

    public CacheRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Reads the cached long URL for a short code.
     *
     * @param shortCode the short code to look up
     * @return the cached long URL, or empty on a cache miss
     */
    public Optional<String> get(@NonNull String shortCode) {
        String value = redisTemplate.opsForValue().get(shortCode);
        return value != null ? Optional.of(value) : Optional.empty();
    }

    /**
     * Caches a short-code → long-URL mapping with the standard 24-hour TTL.
     *
     * @param shortCode the short code (key)
     * @param longUrl   the original long URL (value)
     */
    public void put(String shortCode, String longUrl) {
        if (shortCode == null || longUrl == null) {
            return;
        }
        redisTemplate.opsForValue().set(shortCode, longUrl, TTL);
    }

    /**
     * Removes a short code from the cache.
     *
     * @param shortCode the short code to evict
     */
    public void evict(String shortCode) {
        if (shortCode == null) {
            return; // Guard against null keys which would cause RedisTemplate to throw
        }
        redisTemplate.delete(shortCode);
    }
}
