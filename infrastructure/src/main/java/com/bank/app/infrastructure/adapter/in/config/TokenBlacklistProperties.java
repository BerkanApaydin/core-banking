package com.bank.app.infrastructure.adapter.in.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.concurrent.TimeUnit;

@ConfigurationProperties(prefix = "app.security.token-blacklist")
public record TokenBlacklistProperties(
        @DefaultValue("1000") long minTtlMs,
        @DefaultValue("2592000000") long maxTtlMs
) {
    public TokenBlacklistProperties {
        if (minTtlMs < 1) {
            throw new IllegalArgumentException("Blacklist min TTL must be at least 1 ms");
        }
        if (maxTtlMs < minTtlMs) {
            throw new IllegalArgumentException("Blacklist max TTL must not be less than min TTL");
        }
    }

    /** Default upper bound: 30 days (also the Caffeine {@code expireAfterWrite} ceiling). */
    public static long defaultMaxTtlMs() {
        return TimeUnit.DAYS.toMillis(30);
    }
}
