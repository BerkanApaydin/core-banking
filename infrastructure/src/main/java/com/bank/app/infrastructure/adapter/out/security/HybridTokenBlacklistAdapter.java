package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.user.application.port.out.RevocationStoreUnavailableException;
import com.bank.app.user.application.port.out.TokenBlacklistPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Migration backend: new revocations are persisted in PostgreSQL and Redis;
 * both stores are read so pre-migration Redis-only revocations remain valid.
 * After all old JWTs expire, the database backend can be used alone.
 */
public final class HybridTokenBlacklistAdapter implements TokenBlacklistPort {

    private static final Logger log = LoggerFactory.getLogger(HybridTokenBlacklistAdapter.class);

    private final DatabaseTokenBlacklistAdapter database;
    private final RedisTokenBlacklistAdapter redis;
    private final TokenBlacklistAdapter local;

    public HybridTokenBlacklistAdapter(DatabaseTokenBlacklistAdapter database,
            RedisTokenBlacklistAdapter redis, TokenBlacklistAdapter local) {
        this.database = database;
        this.redis = redis;
        this.local = local;
    }

    @Override
    public void blacklist(String token, long expirationMs) {
        database.blacklist(token, expirationMs);
        try {
            // Old pods in a rolling deployment still consult Redis. A failed
            // write cannot be reported as successful until they are gone.
            redis.blacklist(token, expirationMs);
        } catch (RuntimeException e) {
            log.warn("Hybrid revocation Redis write failed: {}", e.getClass().getSimpleName());
            throw new RevocationStoreUnavailableException(e);
        }
        local.blacklist(token, expirationMs);
    }

    @Override
    public boolean isBlacklisted(String token) {
        if (local.isBlacklisted(token) || database.isBlacklisted(token)) {
            return true;
        }
        try {
            return redis.isBlacklisted(token);
        } catch (RuntimeException e) {
            log.warn("Hybrid revocation Redis read failed: {}", e.getClass().getSimpleName());
            throw new RevocationStoreUnavailableException(e);
        }
    }

    @Override
    public void cleanExpired() {
        database.cleanExpired();
        redis.cleanExpired();
        local.cleanExpired();
    }
}
