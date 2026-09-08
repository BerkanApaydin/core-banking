package com.bank.app.transfer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.transfer")
public record TransferProperties(
        @DefaultValue("24") int cancellationWindowHours,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("500") long initialDelayMs,
        @DefaultValue("2000") long maxDelayMs,
        @DefaultValue("100") int maxPageSize
) {
    public TransferProperties {
        // Fail fast: maxAttempts < 1 would silently skip the use case body and
        // throw IllegalStateException from the retry aspect instead.
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1: " + maxAttempts);
        }
    }
}
