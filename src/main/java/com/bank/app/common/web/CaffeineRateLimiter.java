package com.bank.app.common.web;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(name = "app.security.rate-limit.backend", havingValue = "caffeine", matchIfMissing = true)
public class CaffeineRateLimiter implements RateLimiter {

    private final int maxRequests;
    private final long timeWindowMs;
    private final Cache<String, RateLimitInfo> cache;

    public CaffeineRateLimiter(
            @Value("${app.security.rate-limit.max-requests}") int maxRequests,
            @Value("${app.security.rate-limit.time-window-ms}") long timeWindowMs) {
        this.maxRequests = maxRequests;
        this.timeWindowMs = timeWindowMs;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(timeWindowMs, TimeUnit.MILLISECONDS)
                .maximumSize(10000)
                .build();
    }

    @Override
    public boolean tryAcquire(String clientKey) {
        RateLimitInfo info = cache.asMap().compute(clientKey, (k, v) -> {
            if (v == null || v.isExpired()) {
                return new RateLimitInfo(1, timeWindowMs);
            }
            v.requestCount.incrementAndGet();
            return v;
        });
        return info.requestCount.get() <= maxRequests;
    }

    private static class RateLimitInfo {
        final long resetTime;
        final AtomicInteger requestCount;

        RateLimitInfo(int count, long durationMs) {
            this.resetTime = System.currentTimeMillis() + durationMs;
            this.requestCount = new AtomicInteger(count);
        }

        boolean isExpired() {
            return System.currentTimeMillis() > resetTime;
        }
    }
}
