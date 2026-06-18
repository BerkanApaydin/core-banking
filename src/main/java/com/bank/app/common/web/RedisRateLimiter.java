package com.bank.app.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.security.rate-limit.backend", havingValue = "redis")
public class RedisRateLimiter implements RateLimiter {

    private static final String KEY_PREFIX = "rate_limit:";

    private static final String LUA_SCRIPT =
        "local key = KEYS[1]\n" +
        "local window = tonumber(ARGV[1])\n" +
        "local count = redis.call('INCR', key)\n" +
        "if count == 1 then\n" +
        "    redis.call('PEXPIRE', key, window)\n" +
        "end\n" +
        "return count";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript;
    private final int maxRequests;
    private final Duration window;

    public RedisRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${app.security.rate-limit.max-requests}") int maxRequests,
            @Value("${app.security.rate-limit.time-window-ms}") long timeWindowMs) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
        this.maxRequests = maxRequests;
        this.window = Duration.ofMillis(timeWindowMs);
    }

    @Override
    public boolean tryAcquire(String clientKey) {
        String key = KEY_PREFIX + clientKey;
        Long count = redisTemplate.execute(rateLimitScript, List.of(key), String.valueOf(window.toMillis()));
        return count != null && count <= maxRequests;
    }
}
