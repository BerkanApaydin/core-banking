package com.bank.app.user.infrastructure.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class FailedLoginAttemptService {

    private final Cache<String, AtomicInteger> ipCache;
    private final Cache<String, AtomicInteger> usernameCache;
    private final int maxAttempts;
    private final long windowMinutes;

    public FailedLoginAttemptService(
            @Value("${app.security.failed-login.max-attempts:5}") int maxAttempts,
            @Value("${app.security.failed-login.window-minutes:15}") long windowMinutes) {
        this.maxAttempts = maxAttempts;
        this.windowMinutes = windowMinutes;
        this.ipCache = Caffeine.newBuilder()
                .expireAfterWrite(windowMinutes, TimeUnit.MINUTES)
                .maximumSize(10000)
                .build();
        this.usernameCache = Caffeine.newBuilder()
                .expireAfterWrite(windowMinutes, TimeUnit.MINUTES)
                .maximumSize(10000)
                .build();
    }

    public boolean isIpBlocked(String ip) {
        if (maxAttempts < 0) return false;
        AtomicInteger count = ipCache.getIfPresent(ip);
        return count != null && count.get() >= maxAttempts;
    }

    public boolean isUsernameBlocked(String username) {
        if (maxAttempts < 0) return false;
        AtomicInteger count = usernameCache.getIfPresent(username);
        return count != null && count.get() >= maxAttempts;
    }

    public boolean isBlocked(String ip) {
        return isIpBlocked(ip);
    }

    public void recordFailure(String ip) {
        ipCache.asMap().compute(ip, (k, v) -> {
            if (v == null) return new AtomicInteger(1);
            v.incrementAndGet();
            return v;
        });
    }

    public void recordFailureByUsername(String username) {
        usernameCache.asMap().compute(username, (k, v) -> {
            if (v == null) return new AtomicInteger(1);
            v.incrementAndGet();
            return v;
        });
    }

    public void reset(String ip) {
        ipCache.invalidate(ip);
    }

    public void resetByUsername(String username) {
        usernameCache.invalidate(username);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getWindowMinutes() {
        return windowMinutes;
    }
}
