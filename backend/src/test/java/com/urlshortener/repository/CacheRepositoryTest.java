package com.urlshortener.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class CacheRepositoryTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private CacheRepository repository;

    @BeforeEach
    void setUp() {
        repository = new CacheRepository(redisTemplate);
    }

    @Test
    @DisplayName("get() returns the cached value on a cache hit")
    void getReturnsValueOnCacheHit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("abc1234")).thenReturn("https://example.com/page");

        Optional<String> result = repository.get("abc1234");

        assertThat(result).contains("https://example.com/page");
    }

    @Test
    @DisplayName("get() returns empty on a cache miss")
    void getReturnsEmptyOnCacheMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("missing")).thenReturn(null);

        Optional<String> result = repository.get("missing");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("put() stores the value with a 24-hour TTL")
    @SuppressWarnings("null") // eq()/ArgumentCaptor.capture() aren't @NonNull-annotated (false positive)
    void putStoresValueWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        repository.put("abc1234", "https://example.com/page");

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(eq("abc1234"), eq("https://example.com/page"), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    @DisplayName("put() is a no-op when the short code is null")
    void putIgnoresNullShortCode() {
        repository.put(null, "https://example.com/page");

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("put() is a no-op when the long URL is null")
    void putIgnoresNullLongUrl() {
        repository.put("abc1234", null);

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("evict() deletes the key from the cache")
    void evictDeletesKey() {
        repository.evict("abc1234");

        verify(redisTemplate).delete("abc1234");
    }

    @Test
    @DisplayName("evict() is a no-op when the key is null")
    void evictIgnoresNullKey() {
        repository.evict(null);

        verifyNoInteractions(redisTemplate);
    }
}
