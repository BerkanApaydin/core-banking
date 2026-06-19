package com.bank.app.common.web;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(name = "app.security.rate-limit.backend", havingValue = "caffeine", matchIfMissing = true)
public class CaffeineRateLimiter implements RateLimiter {

    private final int maxRequests;
    private final long timeWindowMs;
    private final Cache<String, RateLimitInfo> cache;
    private final Clock clock;
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();

    @Autowired
    public CaffeineRateLimiter(
            @Value("${app.security.rate-limit.max-requests}") int maxRequests,
            @Value("${app.security.rate-limit.time-window-ms}") long timeWindowMs) {
        this(maxRequests, timeWindowMs, 10000, Clock.systemDefaultZone());
    }

    CaffeineRateLimiter(int maxRequests, long timeWindowMs, int maxCacheSize, Clock clock) {
        this.maxRequests = maxRequests;
        this.timeWindowMs = timeWindowMs;
        this.clock = clock;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(timeWindowMs, TimeUnit.MILLISECONDS)
                .maximumSize(maxCacheSize)
                .build();
    }

    @Override
    public boolean tryAcquire(String clientKey) {
        RateLimitInfo info = cache.asMap().compute(clientKey, (k, v) -> {
            if (v == null || v.isExpired(clock)) {
                missCount.incrementAndGet();
                return new RateLimitInfo(1, timeWindowMs, clock);
            }
            hitCount.incrementAndGet();
            v.requestCount.incrementAndGet();
            return v;
        });
        return info.requestCount.get() <= maxRequests;
    }

    public long getHitCount() {
        return hitCount.get();
    }

    public long getMissCount() {
        return missCount.get();
    }

    public long getRequestCount() {
        return hitCount.get() + missCount.get();
    }

    private static class RateLimitInfo {
        final long resetTime;
        final AtomicInteger requestCount;

        RateLimitInfo(int count, long durationMs, Clock clock) {
            this.resetTime = clock.millis() + durationMs;
            this.requestCount = new AtomicInteger(count);
        }

        boolean isExpired(Clock clock) {
            return clock.millis() > resetTime;
        }
    }
}
