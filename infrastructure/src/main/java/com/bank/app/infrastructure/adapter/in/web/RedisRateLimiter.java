package com.bank.app.infrastructure.adapter.in.web;

import com.bank.app.common.application.port.out.ClockProviderPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.security.rate-limit.backend", havingValue = "redis")
public class RedisRateLimiter implements RateLimiter {

    private static final String KEY_PREFIX = "rate_limit:sliding:";

    /**
     * One key only: a cluster transaction/script cannot touch a second
     * dynamically built key ({@code key .. ':seq'}) without a CROSSSLOT
     * failure. The z-set member is a UUID generated in Java instead.
     */
    private static final String LUA_SLIDING_WINDOW =
        "local key = KEYS[1]\n" +
        "local now = tonumber(ARGV[1])\n" +
        "local window = tonumber(ARGV[2])\n" +
        "local maxReq = tonumber(ARGV[3])\n" +
        "local member = ARGV[4]\n" +
        "local windowStart = now - window\n" +
        "redis.call('ZREMRANGEBYSCORE', key, '-inf', windowStart)\n" +
        "local count = redis.call('ZCARD', key)\n" +
        "if count < maxReq then\n" +
        "    redis.call('ZADD', key, now, member)\n" +
        "    redis.call('PEXPIRE', key, window)\n" +
        "    return 1\n" +
        "else\n" +
        "    return 0\n" +
        "end";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> slidingWindowScript;
    private final int maxRequests;
    private final long timeWindowMs;
    private final Clock clock;

    @Autowired
    public RedisRateLimiter(
            StringRedisTemplate redisTemplate,
            RateLimitProperties rateLimitProperties,
            ClockProviderPort clockProvider) {
        this(redisTemplate, rateLimitProperties,
                clockProvider != null ? clockProvider.clock() : Clock.systemUTC());
    }

    public RedisRateLimiter(
            StringRedisTemplate redisTemplate,
            RateLimitProperties rateLimitProperties) {
        this(redisTemplate, rateLimitProperties, Clock.systemUTC());
    }

    RedisRateLimiter(
            StringRedisTemplate redisTemplate,
            RateLimitProperties rateLimitProperties,
            Clock clock) {
        this.redisTemplate = redisTemplate;
        this.slidingWindowScript = new DefaultRedisScript<>(LUA_SLIDING_WINDOW, Long.class);
        this.maxRequests = rateLimitProperties.maxRequests();
        this.timeWindowMs = rateLimitProperties.timeWindowMs();
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public boolean tryAcquire(String clientKey) {
        return tryAcquire(clientKey, maxRequests, timeWindowMs);
    }

    @Override
    public boolean tryAcquire(String clientKey, int maxRequests, long timeWindowMs) {
        if (clientKey == null) {
            throw new IllegalArgumentException("clientKey must not be null");
        }
        String key = KEY_PREFIX + clientKey;
        long now = clock.millis();
        List<String> keys = Objects.requireNonNull(List.of(Objects.requireNonNull(key)));
        Long result = redisTemplate.execute(
                Objects.requireNonNull(slidingWindowScript),
                keys,
                String.valueOf(now),
                String.valueOf(timeWindowMs),
                String.valueOf(maxRequests),
                UUID.randomUUID().toString());
        return result != null && result == 1;
    }
}
