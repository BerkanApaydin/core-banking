package com.bank.app.infrastructure.adapter.in.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(
        @DefaultValue("5") int maxRetries,
        @DefaultValue("50") int batchSize,
        @DefaultValue("2") int partitionCount,
        @DefaultValue("2000") long pollDelayMs,
        // Retention window for the OutboxRetentionScheduler: processed rows and
        // their handler dedup keys older than this are deleted. Unprocessed and
        // dead-letter rows are never touched (recovery source and evidence).
        @DefaultValue("30") int retentionDays
) {}
