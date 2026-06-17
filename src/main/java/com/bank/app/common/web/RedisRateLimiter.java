package com.bank.app.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "app.security.rate-limit.backend", havingValue = "redis")
public class RedisRateLimiter implements RateLimiter {

    private static final String KEY_PREFIX = "rate_limit:";

    private final StringRedisTemplate redisTemplate;
    private final int maxRequests;
    private final Duration window;

    public RedisRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${app.security.rate-limit.max-requests}") int maxRequests,
            @Value("${app.security.rate-limit.time-window-ms}") long timeWindowMs) {
        this.redisTemplate = redisTemplate;
        this.maxRequests = maxRequests;
        this.window = Duration.ofMillis(timeWindowMs);
    }

    @Override
    public boolean tryAcquire(String clientKey) {
        String key = KEY_PREFIX + clientKey;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, window);
        }
        return count != null && count <= maxRequests;
    }
}
