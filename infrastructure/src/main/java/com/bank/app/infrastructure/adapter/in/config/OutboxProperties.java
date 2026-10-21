package com.bank.app.infrastructure.adapter.in.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
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
        @DefaultValue("30") long workerTimeoutSeconds,
        // V57: exponential retry backoff. A failed event is not selected
        // again until base * 2^(retry-1) seconds have passed (capped at
        // max). Without it a poison event burned all retries within a couple
        // of poll cycles and dead-lettered long before a transient condition
        // could clear.
        @DefaultValue("5") int retryBackoffBaseSeconds,
        @DefaultValue("300") int retryBackoffMaxSeconds,
        // Connections reserved for WEB traffic. The outbox workers hold one
        // Hikari connection each (REQUIRES_NEW), and they share the pool
        // with the request threads: workers + web must fit the pool, or a
        // busy outbox starves the money path (and vice versa). The poller
        // caps its worker count at poolSize - reserve.
        @DefaultValue("8") int webConnectionReserve
) {
    @ConstructorBinding
    public OutboxProperties {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("Outbox max retries must not be negative");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Outbox batch size must be positive");
        }
        // 0 is the single-partition mode and stays valid.
        if (partitionCount < 0) {
            throw new IllegalArgumentException("Outbox partition count must not be negative");
        }
        if (pollDelayMs < 0) {
            throw new IllegalArgumentException("Outbox poll delay must not be negative");
        }
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("Outbox retention days must be positive");
        }
        if (workerThreadsPerPartition <= 0) {
            throw new IllegalArgumentException("Outbox workers per partition must be positive");
        }
        if (maxWorkerThreads <= 0) {
            throw new IllegalArgumentException("Outbox max workers must be positive");
        }
        if (workerTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("Outbox worker timeout must be positive");
        }
        if (retryBackoffBaseSeconds <= 0) {
            throw new IllegalArgumentException("Outbox retry backoff base must be positive");
        }
        if (retryBackoffMaxSeconds < retryBackoffBaseSeconds) {
            throw new IllegalArgumentException(
                    "Outbox retry backoff max must not be smaller than the base");
        }
        if (webConnectionReserve < 0) {
            throw new IllegalArgumentException("Outbox web connection reserve must not be negative");
        }
    }

    /**
     * Convenience constructor for the pre-backoff settings: the retry
     * backoff and the web-connection reserve keep their defaults.
     */
    public OutboxProperties(int maxRetries, int batchSize, int partitionCount, long pollDelayMs,
            int retentionDays, int workerThreadsPerPartition, int maxWorkerThreads,
            long workerTimeoutSeconds) {
        this(maxRetries, batchSize, partitionCount, pollDelayMs, retentionDays,
                workerThreadsPerPartition, maxWorkerThreads, workerTimeoutSeconds,
                5, 300, 8);
    }
}
