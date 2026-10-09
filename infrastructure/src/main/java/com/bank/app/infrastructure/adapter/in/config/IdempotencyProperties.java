package com.bank.app.infrastructure.adapter.in.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.idempotency")
public record IdempotencyProperties(
        @DefaultValue("24") int expirationHours,
        @DefaultValue("0 0 * * * *") String cleanupCron,
        // Retry policy for the generic @Idempotent guard. Lives here (not on
        // TransferProperties) because infrastructure must not depend on the
        // transfer module; transfer's own retry aspect keeps reading
        // app.transfer.* for its use-case retries.
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("500") long initialDelayMs
) {}
