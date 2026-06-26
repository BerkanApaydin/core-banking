package com.bank.app.common.adapter.in.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.idempotency")
public record IdempotencyProperties(
        @DefaultValue("24") int expirationHours,
        @DefaultValue("0 0 * * * *") String cleanupCron
) {}
