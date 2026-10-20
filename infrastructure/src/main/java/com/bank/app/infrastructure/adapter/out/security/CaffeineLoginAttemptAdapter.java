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
    private final Cache<String, AtomicInteger> accountIpCache;
    private final Cache<String, AtomicInteger> accountCache;
    private final int maxAttempts;
    private final int globalAccountMaxAttempts;
    private final long windowMinutes;

    public CaffeineLoginAttemptAdapter(LoginAttemptProperties properties) {
        this.maxAttempts = properties.maxAttempts();
        this.globalAccountMaxAttempts = properties.globalAccountMaxAttempts();
        this.windowMinutes = properties.windowMinutes();
        this.ipCache = newCounterCache(properties.cacheMaxSize(), windowMinutes);
        this.accountIpCache = newCounterCache(properties.cacheMaxSize(), windowMinutes);
        this.accountCache = newCounterCache(properties.cacheMaxSize(), windowMinutes);
    }

    private static Cache<String, AtomicInteger> newCounterCache(long maximumSize, long windowMinutes) {
        return Caffeine.newBuilder()
                .expireAfterWrite(windowMinutes, TimeUnit.MINUTES)
                .maximumSize(maximumSize)
                .build();
    }

    @Override
    public boolean isIpBlocked(String ip) {
        if (maxAttempts < 0 || ip == null) return false;
        AtomicInteger count = ipCache.getIfPresent(ip);
        return count != null && count.get() >= maxAttempts;
    }

    @Override
    public boolean isUsernameBlocked(String username, String ip) {
        if (maxAttempts < 0 || username == null) return false;
        String normalized = normalizeUsername(username);
        // Account + IP: the tight budget. A failure from THIS client only
        // blocks the account for THIS client.
        AtomicInteger pair = accountIpCache.getIfPresent(pairKey(normalized, ip));
        if (pair != null && pair.get() >= maxAttempts) {
            return true;
        }
        // Account only: the loose budget that still stops a distributed
        // attack, which the pair counter cannot see.
        AtomicInteger account = accountCache.getIfPresent(normalized);
        return account != null && account.get() >= globalAccountMaxAttempts;
    }

    @Override
    public void recordFailure(String ip, String username) {
        recordIpFailure(ip);
        recordUsernameFailure(username, ip);
    }

    public void recordIpFailure(String ip) {
        // Null keys crash Caffeine (invalidate/getIfPresent/compute reject
        // null): a missing username/ip must be a no-op, never an NPE that
        // masks the authentication result.
        if (ip == null) return;
        increment(ipCache, ip);
    }

    public void recordUsernameFailure(String username, String ip) {
        if (username == null) return;
        String normalized = normalizeUsername(username);
        increment(accountIpCache, pairKey(normalized, ip));
        increment(accountCache, normalized);
    }

    private static void increment(Cache<String, AtomicInteger> cache, String key) {
        cache.asMap().compute(key, (k, v) -> {
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
        String normalized = normalizeUsername(username);
        accountCache.invalidate(normalized);
        accountIpCache.asMap().keySet().removeIf(key -> key.startsWith(normalized + "|"));
    }

    @Override
    public int getWindowMinutes() {
        return (int) windowMinutes;
    }

    static String normalizeUsername(String username) {
        return username == null ? null : username.toLowerCase(Locale.ROOT);
    }

    /**
     * Account + IP key. A null IP yields a key without the IP segment, so an
     * unresolvable client still consumes (and is bounded by) one bucket.
     */
    static String pairKey(String normalizedUsername, String ip) {
        return normalizedUsername + "|" + (ip == null ? "-" : ip);
    }
}
