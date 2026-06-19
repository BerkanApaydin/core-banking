package com.bank.app.user.web;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class FailedLoginAttemptService {

    private final Cache<String, AtomicInteger> cache;
    private final int maxAttempts;
    private final long windowMinutes;

    public FailedLoginAttemptService(
            @Value("${app.security.failed-login.max-attempts:5}") int maxAttempts,
            @Value("${app.security.failed-login.window-minutes:1}") long windowMinutes) {
        this.maxAttempts = maxAttempts;
        this.windowMinutes = windowMinutes;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(windowMinutes, TimeUnit.MINUTES)
                .maximumSize(10000)
                .build();
    }

    public boolean isBlocked(String ip) {
        if (maxAttempts < 0) return false;
        AtomicInteger count = cache.getIfPresent(ip);
        return count != null && count.get() >= maxAttempts;
    }

    public void recordFailure(String ip) {
        cache.asMap().compute(ip, (k, v) -> {
            if (v == null) return new AtomicInteger(1);
            v.incrementAndGet();
            return v;
        });
    }

    public void reset(String ip) {
        cache.invalidate(ip);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getWindowMinutes() {
        return windowMinutes;
    }
}
