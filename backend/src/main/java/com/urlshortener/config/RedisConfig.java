package com.urlshortener.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Spring configuration for the Redis (ElastiCache) cache layer, using Lettuce
 * as the client.
 *
 * <p>Host and port come from configuration (environment-backed
 * {@code REDIS_HOST} / {@code REDIS_PORT}), never hardcoded. Both keys (short
 * codes) and values (long URLs) are plain strings, so a {@link RedisTemplate}
 * of {@code <String, String>} with string serializers on key and value is all
 * the cache-aside layer needs.
 */
@Configuration
public class RedisConfig {

    private final String host;
    private final int port;

    public RedisConfig(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port) {
        this.host = host;
        this.port = port;
    }

    /** Lettuce-backed connection factory pointed at the configured endpoint. */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        return new LettuceConnectionFactory(config);
    }

    /** String-to-string template used by the cache repository. */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer serializer = new StringRedisSerializer();
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        return template;
    }
}
