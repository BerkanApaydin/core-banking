package com.bank.app.common.application.port.out;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyPortEntryTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 22, 12, 0);

    @Test
    void shouldDefaultRequestHashAndReplayabilityForLegacyConstructor() {
        IdempotencyPort.Entry entry = new IdempotencyPort.Entry(
                "key", "COMPLETED", "{}", 200, CREATED_AT);

        assertThat(entry.requestHash()).isNull();
        assertThat(entry.replayable()).isTrue();
    }

    @Test
    void shouldKeepRequestHashAndDefaultReplayability() {
        IdempotencyPort.Entry entry = new IdempotencyPort.Entry(
                "key", "COMPLETED", "{}", 200, CREATED_AT, "hash");

        assertThat(entry.requestHash()).isEqualTo("hash");
        assertThat(entry.replayable()).isTrue();
    }

    @Test
    void shouldKeepExplicitReplayabilityFromCanonicalConstructor() {
        IdempotencyPort.Entry entry = new IdempotencyPort.Entry(
                "key", "COMPLETED", "{}", 200, CREATED_AT, "hash", false);

        assertThat(entry.requestHash()).isEqualTo("hash");
        assertThat(entry.replayable()).isFalse();
    }
}
