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
     * stays so existing deployments keep working. Runtime code must inject the
     * resolved {@code AccountInfoCache} bean from
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
            @DefaultValue("1000") long maximumSize,
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
