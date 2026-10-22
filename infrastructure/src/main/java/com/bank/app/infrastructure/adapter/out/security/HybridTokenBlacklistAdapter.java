package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.common.domain.TokenDigest;
import com.bank.app.user.application.port.out.RevocationStoreUnavailableException;
import com.bank.app.user.application.port.out.TokenBlacklistPort;
import com.bank.app.infrastructure.adapter.in.config.TokenBlacklistProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Migration backend: new revocations are persisted in PostgreSQL and Redis;
 * both stores are read so pre-migration Redis-only revocations remain valid.
 * After all old JWTs expire, the database backend can be used alone.
 *
 * <p><b>PERF: the database lookup is cached negatively.</b> The durable
 * revocation check runs on every authenticated request, and a database
 * lookup consumes a connection from the SAME Hikari pool the money path
 * uses — the revocation check was competing with transfers for connections.
 * A short-lived NEGATIVE cache ("the database confirmed this token is not
 * revoked") keeps a busy session off the database entirely.
 *
 * <p>Correctness of the cache:
 * <ul>
 *   <li>Only a NOT-revoked answer is cached. A confirmed revocation is
 *       never cached: it is answered from the local overlay (written on
 *       every revoke) and from the shared store.</li>
 *   <li>The local overlay is checked FIRST, so a revocation performed on
 *       this pod is effective immediately.</li>
 *   <li>A cross-pod revocation becomes visible after at most the
 *       configured TTL (5s by default) — far inside the 15-minute window
 *       already accepted for role changes on non-admin paths (see
 *       docs/decisions/token-version.md).</li>
 * </ul>
 */
public final class HybridTokenBlacklistAdapter implements TokenBlacklistPort {

    private static final Logger log = LoggerFactory.getLogger(HybridTokenBlacklistAdapter.class);

    private final DatabaseTokenBlacklistAdapter database;
    private final RedisTokenBlacklistAdapter redis;
    private final TokenBlacklistAdapter local;
    /** Token digest -> "the database confirmed this token is not revoked". */
    private final Cache<String, Boolean> notRevoked;

    public HybridTokenBlacklistAdapter(DatabaseTokenBlacklistAdapter database,
            RedisTokenBlacklistAdapter redis, TokenBlacklistAdapter local,
            TokenBlacklistProperties properties) {
        this.database = database;
        this.redis = redis;
        this.local = local;
        if (properties.negativeCacheTtlMs() > 0) {
            this.notRevoked = Caffeine.newBuilder()
                    .expireAfterWrite(properties.negativeCacheTtlMs(), TimeUnit.MILLISECONDS)
                    .maximumSize(properties.negativeCacheMaxSize())
                    .build();
        } else {
            this.notRevoked = null;
        }
    }

    /** Convenience constructor for callers that do not tune the negative cache. */
    public HybridTokenBlacklistAdapter(DatabaseTokenBlacklistAdapter database,
            RedisTokenBlacklistAdapter redis, TokenBlacklistAdapter local) {
        this(database, redis, local, new TokenBlacklistProperties(1_000L, 2_592_000_000L));
    }

    @Override
    public void blacklist(String token, long expirationMs) {
        String digest = TokenDigest.sha256Hex(token);
        // Invalidate FIRST: a cached "not revoked" answer must never outlive
        // the revocation, not even while the shared writes are in flight.
        if (notRevoked != null) {
            notRevoked.invalidate(digest);
        }
        // Local overlay immediately: this pod is fail-closed even if both
        // shared stores are unavailable.
        local.blacklist(token, expirationMs);

        RuntimeException failure = null;
        try {
            database.blacklist(token, expirationMs);
        } catch (RuntimeException e) {
            failure = e;
            log.warn("Hybrid revocation database write failed: {}", e.getClass().getSimpleName());
        }
        try {
            // Old pods in a rolling deployment still consult Redis. The write
            // is attempted even after a database failure; order must not hide
            // one store's outage behind the other's.
            redis.blacklist(token, expirationMs);
        } catch (RuntimeException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
            log.warn("Hybrid revocation Redis write failed: {}", e.getClass().getSimpleName());
        } finally {
            if (notRevoked != null) {
                notRevoked.invalidate(digest);
            }
        }
        if (failure != null) {
            // A failed shared write cannot be reported as successful: legacy
            // pods (and, for the database, a restart) would still accept the
            // token. The local revocation above stays in effect.
            throw new RevocationStoreUnavailableException(failure);
        }
    }

    @Override
    public boolean isBlacklisted(String token) {
        // 1. Local overlay: a revocation performed on THIS pod, effective
        //    immediately and without any shared lookup.
        if (local.isBlacklisted(token)) {
            return true;
        }
        // 2. Shared Redis store: every revocation written while the hybrid
        //    backend is active is in Redis as well, so a cross-pod
        //    revocation is caught here.
        try {
            if (redis.isBlacklisted(token)) {
                return true;
            }
        } catch (RuntimeException e) {
            log.warn("Hybrid revocation Redis read failed: {}", e.getClass().getSimpleName());
            throw new RevocationStoreUnavailableException(e);
        }
        // 3. Durable store, with a short negative cache. The cache holds a
        //    "not revoked" answer only, so a confirmed revocation is never
        //    served from it.
        String digest = TokenDigest.sha256Hex(token);
        if (notRevoked != null && notRevoked.getIfPresent(digest) != null) {
            return false;
        }
        if (database.isBlacklisted(token)) {
            return true;
        }
        if (notRevoked != null) {
            notRevoked.put(digest, Boolean.TRUE);
        }
        return false;
    }

    @Override
    public void cleanExpired() {
        database.cleanExpired();
        redis.cleanExpired();
        local.cleanExpired();
    }
}
