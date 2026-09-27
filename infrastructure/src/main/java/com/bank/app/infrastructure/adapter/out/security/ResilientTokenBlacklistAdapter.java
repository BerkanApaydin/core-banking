package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.user.application.port.out.TokenBlacklistPort;
import com.bank.app.user.application.port.out.RevocationStoreUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Redis blacklist with a local overlay for confirmed revocations. The local
 * copy continues denying a token on this pod if Redis later loses that entry.
 *
 * <p>When Redis is unavailable, an otherwise valid token cannot be accepted:
 * another pod may have revoked it. Reads and writes fail closed with a 503 at
 * the web boundary. The local copy remains useful after Redis recovers.
 */
@Component
@Primary
@ConditionalOnProperty(name = "app.security.token-blacklist.backend", havingValue = "redis")
public class ResilientTokenBlacklistAdapter implements TokenBlacklistPort {

    private static final Logger log = LoggerFactory.getLogger(ResilientTokenBlacklistAdapter.class);

    private final RedisTokenBlacklistAdapter redis;
    private final TokenBlacklistAdapter localFallback;

    public ResilientTokenBlacklistAdapter(RedisTokenBlacklistAdapter redis,
            TokenBlacklistAdapter localFallback) {
        this.redis = redis;
        this.localFallback = localFallback;
    }

    @Override
    public void blacklist(String token, long expirationMs) {
        // Shared write first: after a failed logout, the caller can retry on
        // this pod once Redis recovers. A local-only write would block that
        // retry in the authentication filter without revoking other pods.
        try {
            redis.blacklist(token, expirationMs);
        } catch (RuntimeException e) {
            log.warn("Redis blacklist write failed; shared revocation unavailable: {}",
                    e.getClass().getSimpleName());
            throw new RevocationStoreUnavailableException(e);
        }
        localFallback.blacklist(token, expirationMs);
    }

    @Override
    public boolean isBlacklisted(String token) {
        // An already confirmed local revocation remains effective even if
        // Redis loses its entry later.
        if (localFallback.isBlacklisted(token)) {
            return true;
        }
        try {
            return redis.isBlacklisted(token);
        } catch (RuntimeException e) {
            log.warn("Redis blacklist read failed; refusing authenticated request: {}",
                    e.getClass().getSimpleName());
            throw new RevocationStoreUnavailableException(e);
        }
    }

    @Override
    public void cleanExpired() {
        try {
            redis.cleanExpired();
        } catch (RuntimeException e) {
            log.warn("Redis blacklist cleanup failed: {}", e.getClass().getSimpleName());
        }
        localFallback.cleanExpired();
    }
}
