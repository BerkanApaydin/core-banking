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
        @DefaultValue("30") int retentionDays,
        // Audit: worker sizing was magic literals (4 per partition, max 12) in
        // OutboxPoller. I/O-bound handler work scales with partitions, but each
        // worker holds a REQUIRES_NEW transaction (one Hikari connection):
        // keep workersPerPartition x partitionCount under the pool size.
        @DefaultValue("4") int workerThreadsPerPartition,
        @DefaultValue("12") int maxWorkerThreads,
        // Audit: the bounded worker wait was a magic literal (30s) in
        // OutboxPoller. Must stay aligned with the transaction timeout
        // (app.transaction.timeout-seconds): an untimed get() parks the
        // partition thread for the full tx timeout when a handler hangs.
        @DefaultValue("30") long workerTimeoutSeconds
) {
    public OutboxProperties {
        if (workerThreadsPerPartition <= 0) {
            throw new IllegalArgumentException("Outbox workers per partition must be positive");
        }
        if (maxWorkerThreads <= 0) {
            throw new IllegalArgumentException("Outbox max workers must be positive");
        }
        if (workerTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("Outbox worker timeout must be positive");
        }
    }
}
