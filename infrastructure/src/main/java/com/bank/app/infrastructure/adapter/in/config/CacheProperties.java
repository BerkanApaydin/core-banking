package com.bank.app.infrastructure.adapter.in.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.cache.caffeine")
public record CacheProperties(AccountInfoCache accountInfo) {

    public CacheProperties {
        if (accountInfo == null) {
            accountInfo = new AccountInfoCache("caffeine", 1000, 60, 500);
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
         */
        public AccountInfoCache {
            if (backend == null || backend.isBlank()) {
                backend = "caffeine";
            }
        }
    }
}
