package com.bank.app.common.adapter.in.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Component
@ConditionalOnProperty(name = "app.security.rate-limit.backend", havingValue = "redis")
public class RedisRateLimiter implements RateLimiter {

    private static final String KEY_PREFIX = "rate_limit:sliding:";

    private static final String LUA_SLIDING_WINDOW =
        "local key = KEYS[1]\n" +
        "local now = tonumber(ARGV[1])\n" +
        "local window = tonumber(ARGV[2])\n" +
        "local maxReq = tonumber(ARGV[3])\n" +
        "local windowStart = now - window\n" +
        "redis.call('ZREMRANGEBYSCORE', key, '-inf', windowStart)\n" +
        "local count = redis.call('ZCARD', key)\n" +
        "if count < maxReq then\n" +
        "    redis.call('ZADD', key, now, now .. ':' .. math.random())\n" +
        "    redis.call('PEXPIRE', key, window)\n" +
        "    return 1\n" +
        "else\n" +
        "    return 0\n" +
        "end";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> slidingWindowScript;
    private final int maxRequests;
    private final long timeWindowMs;

    public RedisRateLimiter(
            StringRedisTemplate redisTemplate,
            RateLimitProperties rateLimitProperties) {
        this.redisTemplate = redisTemplate;
        this.slidingWindowScript = new DefaultRedisScript<>(LUA_SLIDING_WINDOW, Long.class);
        this.maxRequests = rateLimitProperties.getMaxRequests();
        this.timeWindowMs = rateLimitProperties.getTimeWindowMs();
    }

    @Override
    public boolean tryAcquire(String clientKey) {
        if (clientKey == null) {
            throw new IllegalArgumentException("clientKey must not be null");
        }
        String key = KEY_PREFIX + clientKey;
        long now = Instant.now().toEpochMilli();
        List<String> keys = Objects.requireNonNull(List.of(Objects.requireNonNull(key)));
        Long result = redisTemplate.execute(
                Objects.requireNonNull(slidingWindowScript),
                keys,
                String.valueOf(now),
                String.valueOf(timeWindowMs),
                String.valueOf(maxRequests));
        return result != null && result == 1;
    }
}
