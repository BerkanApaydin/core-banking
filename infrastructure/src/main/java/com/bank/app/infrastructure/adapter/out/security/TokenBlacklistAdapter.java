package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.user.application.port.out.TokenBlacklistPort;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.security.token-blacklist.backend", havingValue = "caffeine", matchIfMissing = true)
public class TokenBlacklistAdapter implements TokenBlacklistPort {

    private static final long MAX_TTL_MS = TimeUnit.DAYS.toMillis(30);

    // Value = absolute expiry epoch-millis of the JWT. A revoked token stays
    // blacklisted exactly until its JWT would expire — neither longer (memory
    // bloat) nor shorter (revocation bypass when jwtExpiration > 1 day).
    private final Cache<String, Long> blacklist;

    public TokenBlacklistAdapter() {
        this.blacklist = Caffeine.newBuilder()
                .expireAfterWrite(MAX_TTL_MS, TimeUnit.MILLISECONDS)
                .maximumSize(1_000_000)
                .build();
    }

    @Override
    public void blacklist(String token, long expirationMs) {
        if (expirationMs <= 0) {
            return;
        }
        long ttl = Math.min(Math.max(expirationMs, 1_000L), MAX_TTL_MS);
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
