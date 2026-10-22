package com.bank.app.infrastructure.adapter.in.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyReaperPropertiesTest {

    @Test
    void shouldRejectNullOlderThanAtStartup() {
        // A null bound would NPE inside the scheduled reaper on every tick,
        // turning a bad config into a job that never recovers crash leftovers.
        assertThatThrownBy(() -> new IdempotencyReaperProperties(true, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("olderThan");
    }

    @Test
    void shouldAcceptTheSmallestPositiveDuration() {
        IdempotencyReaperProperties properties =
                new IdempotencyReaperProperties(true, Duration.ofNanos(1));

        assertThat(properties.olderThan()).isEqualTo(Duration.ofNanos(1));
    }
}
