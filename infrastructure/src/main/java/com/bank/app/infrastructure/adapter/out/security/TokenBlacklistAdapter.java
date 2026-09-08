package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.user.application.port.out.TokenBlacklistPort;
import com.bank.app.infrastructure.adapter.in.config.TokenBlacklistProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.security.token-blacklist.backend", havingValue = "caffeine", matchIfMissing = true)
public class TokenBlacklistAdapter implements TokenBlacklistPort {

    private final long minTtlMs;
    private final long maxTtlMs;

    // Value = absolute expiry epoch-millis of the JWT. A revoked token stays
    // blacklisted exactly until its JWT would expire — neither longer (memory
    // bloat) nor shorter (revocation bypass when jwtExpiration > 1 day).
    // Bounds come from {@code app.security.token-blacklist.min/max-ttl-ms}.
    private final Cache<String, Long> blacklist;

    public TokenBlacklistAdapter(TokenBlacklistProperties properties) {
        this.minTtlMs = properties.minTtlMs();
        this.maxTtlMs = properties.maxTtlMs();
        this.blacklist = Caffeine.newBuilder()
                .expireAfterWrite(maxTtlMs, TimeUnit.MILLISECONDS)
                .maximumSize(1_000_000)
                .build();
    }

    @Override
    public void blacklist(String token, long expirationMs) {
        if (expirationMs <= 0) {
            return;
        }
        long ttl = Math.min(Math.max(expirationMs, minTtlMs), maxTtlMs);
        blacklist.put(token, System.currentTimeMillis() + ttl);
    }

    @Override
    public boolean isBlacklisted(String token) {
        Long expiresAt = blacklist.getIfPresent(token);
        if (expiresAt == null) {
            return false;
        }
        if (System.currentTimeMillis() >= expiresAt) {
            blacklist.invalidate(token);
            return false;
        }
        return true;
    }

    @Override
    public void cleanExpired() {
        blacklist.cleanUp();
    }
}
