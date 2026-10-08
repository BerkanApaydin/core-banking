package com.bank.app.common.adapter.in.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProblemBody")
class ProblemBodyTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("renders UTC ISO-8601 timestamps from the given clock")
    void rendersTimestampFromClock() {
        ProblemBody body = ProblemBody.of(401, "AUTHENTICATION_FAILED", "No session.",
                FIXED, null, "/api/v1/auth/browser/session");

        assertThat(body.timestamp()).isEqualTo("2026-06-01T12:00");
        // Parseable back to a LocalDateTime by any consumer.
        assertThat(LocalDateTime.parse(body.timestamp())).isEqualTo(LocalDateTime.of(2026, 6, 1, 12, 0));
    }

    @Test
    @DisplayName("normalizes blank correlation ids to null (field omitted by renderers)")
    void normalizesBlankCorrelationId() {
        assertThat(ProblemBody.of(400, "C", "m", FIXED, "   ", null).correlationId()).isNull();
        assertThat(ProblemBody.of(400, "C", "m", FIXED, "", null).correlationId()).isNull();
        assertThat(ProblemBody.of(400, "C", "m", FIXED, "corr-1", null).correlationId())
                .isEqualTo("corr-1");
    }

    @Test
    @DisplayName("rejects null code, timestamp and clock")
    void rejectsNulls() {
        assertThatThrownBy(() -> ProblemBody.of(400, null, "m", FIXED, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ProblemBody.of(400, "C", "m", null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProblemBody(400, "C", "m", null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("degrades null message to empty (unresolvable bundle key)")
    void degradesNullMessage() {
        assertThat(ProblemBody.of(500, "GENERAL_INTERNAL_ERROR", null, FIXED, null, null).message())
                .isEmpty();
    }
}
