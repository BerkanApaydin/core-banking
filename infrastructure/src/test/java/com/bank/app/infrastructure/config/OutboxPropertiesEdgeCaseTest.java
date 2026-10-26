package com.bank.app.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OutboxProperties edge cases")
class OutboxPropertiesEdgeCaseTest {

    private static final int MAX_RETRIES = 5;
    private static final int BATCH_SIZE = 50;
    private static final int PARTITIONS = 2;
    private static final long POLL_DELAY = 2000;
    private static final int RETENTION_DAYS = 30;
    private static final int WORKERS_PER_PARTITION = 4;
    private static final int MAX_WORKERS = 12;
    private static final long WORKER_TIMEOUT = 30;
    private static final int BACKOFF_BASE = 5;
    private static final int BACKOFF_MAX = 300;
    private static final int WEB_RESERVE = 8;

    private static OutboxProperties props(int maxRetries, int batchSize, int partitions,
            long pollDelay, int retentionDays, int workersPerPartition, int maxWorkers,
            long workerTimeout, int backoffBase, int backoffMax, int webReserve) {
        return new OutboxProperties(maxRetries, batchSize, partitions, pollDelay, retentionDays,
                workersPerPartition, maxWorkers, workerTimeout, backoffBase, backoffMax, webReserve);
    }

    private static OutboxProperties defaults() {
        return props(MAX_RETRIES, BATCH_SIZE, PARTITIONS, POLL_DELAY, RETENTION_DAYS,
                WORKERS_PER_PARTITION, MAX_WORKERS, WORKER_TIMEOUT, BACKOFF_BASE, BACKOFF_MAX,
                WEB_RESERVE);
    }

    @Test
    @DisplayName("accepts the documented zero-valued modes")
    void shouldAcceptZeroValuedModes() {
        OutboxProperties properties = props(0, BATCH_SIZE, 0, 0, RETENTION_DAYS,
                WORKERS_PER_PARTITION, MAX_WORKERS, WORKER_TIMEOUT, BACKOFF_BASE, BACKOFF_MAX, 0);

        assertThat(properties.maxRetries()).isZero();
        assertThat(properties.partitionCount()).isZero();
        assertThat(properties.pollDelayMs()).isZero();
        assertThat(properties.webConnectionReserve()).isZero();
    }

    @Test
    @DisplayName("accepts a backoff cap equal to the base")
    void shouldAcceptEqualBackoffBounds() {
        OutboxProperties properties = props(MAX_RETRIES, BATCH_SIZE, PARTITIONS, POLL_DELAY,
                RETENTION_DAYS, WORKERS_PER_PARTITION, MAX_WORKERS, WORKER_TIMEOUT,
                300, 300, WEB_RESERVE);

        assertThat(properties.retryBackoffBaseSeconds()).isEqualTo(300);
        assertThat(properties.retryBackoffMaxSeconds()).isEqualTo(300);
    }

    @Test
    @DisplayName("rejects negative retries, poll delay, and reserve")
    void shouldRejectNegativeValues() {
        assertThatThrownBy(() -> props(-1, BATCH_SIZE, PARTITIONS, POLL_DELAY, RETENTION_DAYS,
                WORKERS_PER_PARTITION, MAX_WORKERS, WORKER_TIMEOUT, BACKOFF_BASE, BACKOFF_MAX,
                WEB_RESERVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max retries");
        assertThatThrownBy(() -> props(MAX_RETRIES, BATCH_SIZE, PARTITIONS, -1, RETENTION_DAYS,
                WORKERS_PER_PARTITION, MAX_WORKERS, WORKER_TIMEOUT, BACKOFF_BASE, BACKOFF_MAX,
                WEB_RESERVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("poll delay");
        assertThatThrownBy(() -> props(MAX_RETRIES, BATCH_SIZE, -1, POLL_DELAY, RETENTION_DAYS,
                WORKERS_PER_PARTITION, MAX_WORKERS, WORKER_TIMEOUT, BACKOFF_BASE, BACKOFF_MAX,
                WEB_RESERVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("partition count");
        assertThatThrownBy(() -> props(MAX_RETRIES, BATCH_SIZE, PARTITIONS, POLL_DELAY,
                RETENTION_DAYS, WORKERS_PER_PARTITION, MAX_WORKERS, WORKER_TIMEOUT,
                BACKOFF_BASE, BACKOFF_MAX, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("web connection reserve");
    }

    @Test
    @DisplayName("rejects non-positive batch size and retention")
    void shouldRejectNonPositiveBatchAndRetention() {
        assertThatThrownBy(() -> props(MAX_RETRIES, 0, PARTITIONS, POLL_DELAY, RETENTION_DAYS,
                WORKERS_PER_PARTITION, MAX_WORKERS, WORKER_TIMEOUT, BACKOFF_BASE, BACKOFF_MAX,
                WEB_RESERVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batch size");
        assertThatThrownBy(() -> props(MAX_RETRIES, BATCH_SIZE, PARTITIONS, POLL_DELAY, 0,
                WORKERS_PER_PARTITION, MAX_WORKERS, WORKER_TIMEOUT, BACKOFF_BASE, BACKOFF_MAX,
                WEB_RESERVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retention days");
    }

    @Test
    @DisplayName("rejects non-positive worker sizing")
    void shouldRejectNonPositiveWorkerSizing() {
        assertThatThrownBy(() -> props(MAX_RETRIES, BATCH_SIZE, PARTITIONS, POLL_DELAY,
                RETENTION_DAYS, 0, MAX_WORKERS, WORKER_TIMEOUT, BACKOFF_BASE, BACKOFF_MAX,
                WEB_RESERVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workers per partition");
        assertThatThrownBy(() -> props(MAX_RETRIES, BATCH_SIZE, PARTITIONS, POLL_DELAY,
                RETENTION_DAYS, WORKERS_PER_PARTITION, 0, WORKER_TIMEOUT, BACKOFF_BASE,
                BACKOFF_MAX, WEB_RESERVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max workers");
        assertThatThrownBy(() -> props(MAX_RETRIES, BATCH_SIZE, PARTITIONS, POLL_DELAY,
                RETENTION_DAYS, WORKERS_PER_PARTITION, MAX_WORKERS, 0, BACKOFF_BASE,
                BACKOFF_MAX, WEB_RESERVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("worker timeout");
    }

    @Test
    @DisplayName("rejects an invalid backoff curve")
    void shouldRejectInvalidBackoff() {
        assertThatThrownBy(() -> props(MAX_RETRIES, BATCH_SIZE, PARTITIONS, POLL_DELAY,
                RETENTION_DAYS, WORKERS_PER_PARTITION, MAX_WORKERS, WORKER_TIMEOUT, 0, BACKOFF_MAX,
                WEB_RESERVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("backoff base");
        assertThatThrownBy(() -> props(MAX_RETRIES, BATCH_SIZE, PARTITIONS, POLL_DELAY,
                RETENTION_DAYS, WORKERS_PER_PARTITION, MAX_WORKERS, WORKER_TIMEOUT, 5, 4,
                WEB_RESERVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("backoff max");
    }

    @Test
    @DisplayName("keeps the legacy constructor aligned with current defaults")
    void shouldProvideLegacyConstructorDefaults() {
        OutboxProperties properties = new OutboxProperties(
                MAX_RETRIES, BATCH_SIZE, PARTITIONS, POLL_DELAY, RETENTION_DAYS,
                WORKERS_PER_PARTITION, MAX_WORKERS, WORKER_TIMEOUT);

        assertThat(properties).isEqualTo(defaults());
    }
}
