package com.bank.app.common.adapter.in.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(
        @DefaultValue("5") int maxRetries,
        @DefaultValue("50") int batchSize,
        @DefaultValue("0") int partitionCount,
        @DefaultValue("2000") long pollDelayMs
) {}
