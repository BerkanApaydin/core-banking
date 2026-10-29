package com.bank.app.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R-I10: configuration records reject values that would make their schedulers
 * or pollers misbehave. {@code partitionCount == 0} is the documented
 * single-partition mode and must stay valid.
 */
class PropertiesValidationTest {

    @Test
    void shouldAcceptValidOutboxPropertiesIncludingSinglePartitionMode() {
        assertThatCode(() -> new OutboxProperties(5, 50, 0, 2000, 30, 4, 12, 30))
                .doesNotThrowAnyException();
        assertThat(new OutboxProperties(5, 50, 0, 2000, 30, 4, 12, 30).partitionCount())
                .isZero();
    }

    @Test
    void shouldRejectNegativeOutboxMaxRetries() {
        assertThatThrownBy(() -> new OutboxProperties(-1, 50, 2, 2000, 30, 4, 12, 30))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNonPositiveOutboxBatchSize() {
        assertThatThrownBy(() -> new OutboxProperties(5, 0, 2, 2000, 30, 4, 12, 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutboxProperties(5, -1, 2, 2000, 30, 4, 12, 30))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNegativeOutboxPartitionCount() {
        assertThatThrownBy(() -> new OutboxProperties(5, 50, -1, 2000, 30, 4, 12, 30))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNegativeOutboxPollDelay() {
        assertThatThrownBy(() -> new OutboxProperties(5, 50, 2, -1, 30, 4, 12, 30))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNonPositiveOutboxRetentionDays() {
        assertThatThrownBy(() -> new OutboxProperties(5, 50, 2, 2000, 0, 4, 12, 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutboxProperties(5, 50, 2, 2000, -1, 4, 12, 30))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcceptValidIdempotencyProperties() {
        assertThatCode(() -> new IdempotencyProperties(24, "0 0 * * * *", 3, 500))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectNonPositiveIdempotencyExpirationHours() {
        assertThatThrownBy(() -> new IdempotencyProperties(0, "0 0 * * * *", 3, 500))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyProperties(-1, "0 0 * * * *", 3, 500))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNonPositiveIdempotencyMaxAttempts() {
        assertThatThrownBy(() -> new IdempotencyProperties(24, "0 0 * * * *", 0, 500))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyProperties(24, "0 0 * * * *", -1, 500))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNegativeIdempotencyInitialDelay() {
        assertThatThrownBy(() -> new IdempotencyProperties(24, "0 0 * * * *", 3, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBlankIdempotencyCleanupCron() {
        assertThatThrownBy(() -> new IdempotencyProperties(24, null, 3, 500))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyProperties(24, "   ", 3, 500))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldTreatBlankSchedulerUrlAsNotConfigured() {
        var unset = new SchedulerDataSourceProperties(" ", "bank_scheduler", "pw", 2, true);
        var configured = new SchedulerDataSourceProperties(
                "jdbc:postgresql://localhost:5432/bank_db", "bank_scheduler", "pw", 2, true);

        assertThat(unset.configured()).isFalse();
        assertThat(configured.configured()).isTrue();
    }

    @Test
    void shouldRejectNonPositiveSchedulerPoolSize() {
        assertThatThrownBy(() -> new SchedulerDataSourceProperties(
                "jdbc:postgresql://localhost:5432/bank_db", "bank_scheduler", "pw", 0, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SchedulerDataSourceProperties(
                "jdbc:postgresql://localhost:5432/bank_db", "bank_scheduler", "pw", -1, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
