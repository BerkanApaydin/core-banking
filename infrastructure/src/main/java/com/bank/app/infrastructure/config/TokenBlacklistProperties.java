package com.bank.app.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.token-blacklist")
public record TokenBlacklistProperties(
        @DefaultValue("1000") long minTtlMs,
        @DefaultValue("2592000000") long maxTtlMs,
        /**
         * Lifetime of a "confirmed NOT revoked" answer for the database
         * lookup (milliseconds). 0 disables the cache and every request
         * pays the database round trip.
         *
         * <p>Why it exists: the durable revocation check runs on EVERY
         * authenticated request, and a database lookup consumes a
         * connection from the SAME Hikari pool the money path uses. A
         * short-lived negative cache keeps a busy session (many requests
         * with the same token) off the database entirely.
         *
         * <p>Correctness: the cache is a NEGATIVE cache only — a confirmed
         * revocation is never cached, it is answered from the local overlay
         * (written on every revoke) and from the shared store. A
         * cross-pod revocation therefore becomes visible after at most this
         * TTL, which is far inside the 15-minute window already accepted
         * for role changes on non-admin paths (see
         * docs/decisions/token-version.md).
         */
        @DefaultValue("5000") long negativeCacheTtlMs,
        /** Entry bound for the negative cache (memory guard). */
        @DefaultValue("10000") long negativeCacheMaxSize
) {
    @ConstructorBinding
    public TokenBlacklistProperties {
        if (minTtlMs < 1) {
            throw new IllegalArgumentException("Blacklist min TTL must be at least 1 ms");
        }
        if (maxTtlMs < minTtlMs) {
            throw new IllegalArgumentException("Blacklist max TTL must not be less than min TTL");
        }
        if (negativeCacheTtlMs < 0) {
            throw new IllegalArgumentException("Blacklist negative-cache TTL must not be negative");
        }
        if (negativeCacheMaxSize < 1) {
            throw new IllegalArgumentException("Blacklist negative-cache size must be positive");
        }
    }

    /** Convenience constructor for callers that do not tune the negative cache. */
    public TokenBlacklistProperties(long minTtlMs, long maxTtlMs) {
        this(minTtlMs, maxTtlMs, 5_000L, 10_000L);
    }
}
