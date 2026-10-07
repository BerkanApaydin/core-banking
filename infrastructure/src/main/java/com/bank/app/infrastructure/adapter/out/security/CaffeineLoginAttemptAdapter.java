package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.user.application.port.out.LoginAttemptPort;
import com.bank.app.infrastructure.adapter.in.security.LoginAttemptProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(name = "app.security.failed-login.backend", havingValue = "caffeine", matchIfMissing = true)
public class CaffeineLoginAttemptAdapter implements LoginAttemptPort {

    private final Cache<String, AtomicInteger> ipCache;
    private final Cache<String, AtomicInteger> usernameCache;
    private final int maxAttempts;
    private final long windowMinutes;

    public CaffeineLoginAttemptAdapter(LoginAttemptProperties properties) {
        this.maxAttempts = properties.maxAttempts();
        this.windowMinutes = properties.windowMinutes();
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
        if (maxAttempts < 0 || ip == null) return false;
        AtomicInteger count = ipCache.getIfPresent(ip);
        return count != null && count.get() >= maxAttempts;
    }

    @Override
    public boolean isUsernameBlocked(String username) {
        if (maxAttempts < 0 || username == null) return false;
        AtomicInteger count = usernameCache.getIfPresent(normalizeUsername(username));
        return count != null && count.get() >= maxAttempts;
    }

    @Override
    public void recordFailure(String ip, String username) {
        recordIpFailure(ip);
        recordUsernameFailure(username);
    }

    public void recordIpFailure(String ip) {
        // Null keys crash Caffeine (invalidate/getIfPresent/compute reject
        // null): a missing username/ip must be a no-op, never an NPE that
        // masks the authentication result.
        if (ip == null) return;
        ipCache.asMap().compute(ip, (k, v) -> {
            if (v == null) return new AtomicInteger(1);
            v.incrementAndGet();
            return v;
        });
    }

    public void recordUsernameFailure(String username) {
        if (username == null) return;
        String key = normalizeUsername(username);
        usernameCache.asMap().compute(key, (k, v) -> {
            if (v == null) return new AtomicInteger(1);
            v.incrementAndGet();
            return v;
        });
    }

    @Override
    public void reset(String ip) {
        if (ip == null) return;
        ipCache.invalidate(ip);
    }

    @Override
    public void resetByUsername(String username) {
        if (username == null) return;
        usernameCache.invalidate(normalizeUsername(username));
    }

    @Override
    public int getWindowMinutes() {
        return (int) windowMinutes;
    }

    static String normalizeUsername(String username) {
        return username == null ? null : username.toLowerCase(Locale.ROOT);
    }
}
