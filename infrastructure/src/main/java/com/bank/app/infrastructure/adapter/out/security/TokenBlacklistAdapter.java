package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.common.domain.TokenDigest;
import com.bank.app.user.application.port.out.TokenBlacklistPort;
import com.bank.app.infrastructure.adapter.in.config.TokenBlacklistProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

/**
 * Local (single-instance) token blacklist. Always present: it is the backend
 * when {@code app.security.token-blacklist.backend=caffeine}, and an overlay
 * of confirmed revocations behind {@link ResilientTokenBlacklistAdapter}
 * when the backend is Redis.
 */
@Component
public class TokenBlacklistAdapter implements TokenBlacklistPort {

    private final long minTtlMs;
    private final long maxTtlMs;

    // Value = absolute expiry epoch-millis of the JWT. A revoked token stays
    // blacklisted exactly until its JWT would expire — neither longer (memory
    // bloat) nor shorter (revocation bypass when jwtExpiration > 1 day).
    // Bounds come from {@code app.security.token-blacklist.min/max-ttl-ms}.
    private final Cache<String, Long> blacklist;
    // Time-strategy seam: expiry math uses an injectable clock so tests can
    // pin time instead of sleeping. Production keeps systemUTC.
    private volatile Clock clock = Clock.systemUTC();

    public TokenBlacklistAdapter(TokenBlacklistProperties properties) {
        this.minTtlMs = properties.minTtlMs();
        this.maxTtlMs = properties.maxTtlMs();
        this.blacklist = Caffeine.newBuilder()
                .expireAfterWrite(maxTtlMs, TimeUnit.MILLISECONDS)
                .maximumSize(1_000_000)
                .build();
    }

    /** Test clock injection (time-strategy seam). */
    void setClockForTests(Clock testClock) {
        this.clock = testClock != null ? testClock : Clock.systemUTC();
    }

    @Override
    public void blacklist(String token, long expirationMs) {
        if (expirationMs <= 0) {
            return;
        }
        long ttl = Math.min(Math.max(expirationMs, minTtlMs), maxTtlMs);
        // Hash-only storage like the Redis/DB backends: a cache read must
        // never yield a usable credential (heap-dump disclosure).
        blacklist.put(TokenDigest.sha256Hex(token), clock.millis() + ttl);
    }

    @Override
    public boolean isBlacklisted(String token) {
        Long expiresAt = blacklist.getIfPresent(TokenDigest.sha256Hex(token));
        if (expiresAt == null) {
            return false;
        }
        if (clock.millis() >= expiresAt) {
            blacklist.invalidate(TokenDigest.sha256Hex(token));
            return false;
        }
        return true;
    }

    @Override
    public void cleanExpired() {
        blacklist.cleanUp();
    }
}
