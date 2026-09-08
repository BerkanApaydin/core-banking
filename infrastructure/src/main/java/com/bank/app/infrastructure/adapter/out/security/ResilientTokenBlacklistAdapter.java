package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.user.application.port.out.TokenBlacklistPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Redis blacklist with local degradation: when Redis is unreachable (outage,
 * failover, network partition), revocation checks fall back to the
 * single-instance Caffeine blacklist instead of failing every authenticated
 * request with a 500.
 *
 * <p>Trade-off, stated explicitly: during a Redis outage, revocations made on
 * <em>other</em> instances are not visible here (fail-open across instances),
 * while revocations made on <em>this</em> instance are still honored via the
 * local fallback (every {@code blacklist} call writes both). Total auth outage
 * is considered worse than a bounded cross-instance revocation delay; both
 * paths emit WARN logs and the outage itself is visible via Redis health.
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
        // Local first: even if Redis is down, this instance honors the revocation.
        localFallback.blacklist(token, expirationMs);
        try {
            redis.blacklist(token, expirationMs);
        } catch (RuntimeException e) {
            log.warn("Redis blacklist write failed, revocation kept locally only: {}",
                    e.getMessage());
        }
    }

    @Override
    public boolean isBlacklisted(String token) {
        try {
            return redis.isBlacklisted(token);
        } catch (RuntimeException e) {
            log.warn("Redis blacklist read failed, falling back to local blacklist: {}",
                    e.getMessage());
            return localFallback.isBlacklisted(token);
        }
    }

    @Override
    public void cleanExpired() {
        try {
            redis.cleanExpired();
        } catch (RuntimeException e) {
            log.warn("Redis blacklist cleanup failed: {}", e.getMessage());
        }
        localFallback.cleanExpired();
    }
}
