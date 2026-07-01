package com.bank.app.user.adapter.out.security;

import com.bank.app.user.application.port.out.LoginAttemptPort;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(name = "app.security.failed-login.backend", havingValue = "caffeine", matchIfMissing = true)
public class CaffeineLoginAttemptAdapter implements LoginAttemptPort {

    private final Cache<String, AtomicInteger> ipCache;
    private final Cache<String, AtomicInteger> usernameCache;
    private final int maxAttempts;
    private final long windowMinutes;

    public CaffeineLoginAttemptAdapter(
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

    @Override
    public boolean isIpBlocked(String ip) {
        if (maxAttempts < 0) return false;
        AtomicInteger count = ipCache.getIfPresent(ip);
        return count != null && count.get() >= maxAttempts;
    }

    @Override
    public boolean isUsernameBlocked(String username) {
        if (maxAttempts < 0) return false;
        AtomicInteger count = usernameCache.getIfPresent(username);
        return count != null && count.get() >= maxAttempts;
    }

    @Override
    public void recordFailure(String ip, String username) {
        recordIpFailure(ip);
        recordUsernameFailure(username);
    }

    public void recordIpFailure(String ip) {
        ipCache.asMap().compute(ip, (k, v) -> {
            if (v == null) return new AtomicInteger(1);
            v.incrementAndGet();
            return v;
        });
    }

    public void recordUsernameFailure(String username) {
        usernameCache.asMap().compute(username, (k, v) -> {
            if (v == null) return new AtomicInteger(1);
            v.incrementAndGet();
            return v;
        });
    }

    @Override
    public void reset(String ip) {
        ipCache.invalidate(ip);
    }

    @Override
    public void resetByUsername(String username) {
        usernameCache.invalidate(username);
    }

    @Override
    public int getWindowMinutes() {
        return (int) windowMinutes;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }
}
