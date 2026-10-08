package com.bank.app.infrastructure.adapter.in.handler;

import com.bank.app.common.domain.exception.ErrorCode;
import com.bank.app.user.adapter.in.web.support.ProblemResponses;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shape contract between the two RFC 7807 renderers: the infrastructure
 * {@code ProblemDetailFactory} and the user-module {@code ProblemResponses}
 * must emit identical bodies for identical input (same keys, same values).
 * Both render the shared {@code ProblemBody}; this test pins the drift that
 * motivated the merge (timestamp type, missing correlationId).
 */
@DisplayName("Problem shape contract (factory vs responses)")
class ProblemShapeContractTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);

    @AfterEach
    void reset() {
        ProblemDetailFactory.setClockForTests(null);
        ProblemResponses.setClockForTests(null);
        MDC.remove("correlationId");
    }

    @Test
    @DisplayName("identical input yields identical bodies including correlation id")
    void identicalInputYieldsIdenticalBodies() {
        ProblemDetailFactory.setClockForTests(FIXED);
        ProblemResponses.setClockForTests(FIXED);
        MDC.put("correlationId", "corr-1");

        ResponseEntity<ProblemDetail> fromFactory = ProblemDetailFactory.create(
                ErrorCode.AUTHENTICATION_FAILED, "No active browser session.", null);
        ResponseEntity<ProblemDetail> fromResponses =
                ProblemResponses.unauthorized("No active browser session.", null);

        assertThat(fromFactory.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(fromResponses.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(fromFactory.getBody()).isNotNull();
        assertThat(fromResponses.getBody()).isNotNull();
        assertThat(fromFactory.getBody().getProperties())
                .isEqualTo(fromResponses.getBody().getProperties());
    }

    @Test
    @DisplayName("both omit correlation id outside a request thread")
    void bothOmitCorrelationIdWithoutMdc() {
        ProblemDetailFactory.setClockForTests(FIXED);
        ProblemResponses.setClockForTests(FIXED);

        ResponseEntity<ProblemDetail> fromFactory = ProblemDetailFactory.create(
                ErrorCode.AUTHENTICATION_FAILED, "No active browser session.", null);
        ResponseEntity<ProblemDetail> fromResponses =
                ProblemResponses.unauthorized("No active browser session.", null);

        assertThat(fromFactory.getBody().getProperties()).doesNotContainKey("correlationId");
        assertThat(fromResponses.getBody().getProperties()).doesNotContainKey("correlationId");
        assertThat(fromFactory.getBody().getProperties())
                .isEqualTo(fromResponses.getBody().getProperties());
    }
}
