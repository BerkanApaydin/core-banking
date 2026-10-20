package com.bank.app.infrastructure.adapter.in.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.cache.caffeine")
public record CacheProperties(AccountInfoCache accountInfo) {

    /**
     * Legacy prefix binding ({@code app.cache.caffeine.*}). New deployments
     * should set only the canonical {@code app.cache.account-info.*} keys (see
     * {@link CacheBackendResolution}): canonical wins on conflict. This binding
     * stays so existing deployments keep working — and the shipped
     * {@code application.yml} intentionally keeps the
     * {@code CACHE_ACCOUNT_INFO_*} placeholders HERE, leaving the canonical
     * keys unset, so presence-based resolution can still tell "operator set
     * the canonical key" apart from "only the alias is configured". Runtime
     * code must inject the resolved {@code AccountInfoCache} bean from
     * {@code AccountCacheAliasConfig} instead of this record, so behavior and
     * backend selection can never disagree.
     */
    public CacheProperties {
        if (accountInfo == null) {
            accountInfo = AccountInfoCache.defaults();
        }
    }

    public record AccountInfoCache(
            @DefaultValue("caffeine") String backend,
            /**
             * Entry-count cap of the <em>Caffeine</em> backend only. The Redis
             * backend has no entry-count cap: its region is bounded by the TTL
             * instead (memory is roughly the write rate multiplied by
             * {@code expireAfterWrite}). Do not read this key as a bound on
             * the shared cache — size the broker with {@code maxmemory}.
             */
            @DefaultValue("1000") long maximumSize,
            /**
             * Time-to-live of a cached snapshot, in seconds. Applies to both
             * backends and is the upper bound on how long a stale entry can
             * survive an invalidation that never happened (a Redis outage, a
             * missed eviction).
             */
            @DefaultValue("60") long expireAfterWrite,
            @DefaultValue("500") long evictionBatchSize
    ) {
        /**
         * Snapshot-cache backend selector.
         *
         * <p>Historical prefix ({@code app.cache.caffeine...}) is kept for
         * backward compatibility; the value selects the backend:
         * {@code caffeine} = single-JVM (dev/test/single instance),
         * {@code redis} = shared across replicas (production).
         * New deployments should use the canonical
         * {@code app.cache.account-info.*} keys instead.
         */
        public AccountInfoCache {
            if (backend == null || backend.isBlank()) {
                backend = "caffeine";
            }
        }

        static AccountInfoCache defaults() {
            return new AccountInfoCache("caffeine", 1000, 60, 500);
        }

        /**
         * True when every field holds the binding default — i.e. the operator
         * did not customize this prefix. Used by
         * {@link CacheBackendResolution} to prefer an explicitly customized
         * prefix over an untouched one. Keep the literals in sync with the
         * {@code @DefaultValue}s above.
         */
        public boolean isDefault() {
            return this.equals(defaults());
        }
    }
}
