package com.bank.app.infrastructure.adapter.in.web;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class CaffeineRateLimiter implements RateLimiter {

    private final int maxRequests;
    private final long timeWindowMs;
    private final Cache<String, ConcurrentLinkedDeque<Long>> cache;
    private final Clock clock;
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();

    @Autowired
    public CaffeineRateLimiter(RateLimitProperties rateLimitProperties) {
        this(rateLimitProperties.getMaxRequests(), rateLimitProperties.getTimeWindowMs(), 10000, Clock.systemDefaultZone());
    }

    CaffeineRateLimiter(int maxRequests, long timeWindowMs, int maxCacheSize, Clock clock) {
        this.maxRequests = maxRequests;
        this.timeWindowMs = timeWindowMs;
        this.clock = clock;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(timeWindowMs * 2, TimeUnit.MILLISECONDS)
                .maximumSize(maxCacheSize)
                .build();
    }

    @Override
    public boolean tryAcquire(String clientKey) {
        if (maxRequests <= 0) {
            return false;
        }
        long now = clock.millis();
        long windowStart = now - timeWindowMs;

        boolean[] acquired = new boolean[1];

        cache.asMap().compute(clientKey, (k, v) -> {
            if (v == null) {
                missCount.incrementAndGet();
                ConcurrentLinkedDeque<Long> deque = new ConcurrentLinkedDeque<>();
                deque.addLast(now);
                acquired[0] = true;
                return deque;
            }
            hitCount.incrementAndGet();
            while (!v.isEmpty() && v.peekFirst() < windowStart) {
                v.pollFirst();
            }
            if (v.size() < maxRequests) {
                v.addLast(now);
                acquired[0] = true;
            } else {
                acquired[0] = false;
            }
            return v;
        });

        return acquired[0];
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
}
