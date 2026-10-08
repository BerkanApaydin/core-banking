package com.bank.app.user.adapter.in.web.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemResponsesTest {

    @AfterEach
    void resetClock() {
        ProblemResponses.setClockForTests(null);
    }

    @Test
    void unauthorizedRendersProblemShape() {
        var response = ProblemResponses.unauthorized("No active browser session.", "/api/v1/auth/browser/session");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType().toString())
                .contains("application/problem+json");
        assertThat(response.getBody().getProperties())
                .containsEntry("code", "AUTHENTICATION_FAILED")
                .containsEntry("message", "No active browser session.")
                .containsKey("timestamp");
        // Kills the setTitle VoidMethodCall mutant.
        assertThat(response.getBody().getTitle()).isEqualTo("Unauthorized");
        // Kills the setInstance VoidMethodCall mutant.
        assertThat(response.getBody().getInstance()).isNotNull();
        assertThat(response.getBody().getInstance().toString())
                .isEqualTo("/api/v1/auth/browser/session");
    }

    @Test
    void nullPathOmitsInstance() {
        // Kills the NegateConditionals mutant on (path != null).
        var response = ProblemResponses.unauthorized("x", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getInstance()).isNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Unauthorized");
    }

    @Test
    void forbiddenSetsTitleAndInstance() {
        var response = ProblemResponses.forbidden("Denied.", "/p");

        assertThat(response.getBody().getTitle()).isEqualTo("Forbidden");
        assertThat(response.getBody().getInstance().toString()).isEqualTo("/p");
    }

    @Test
    void forbiddenRendersProblemShape() {
        var response = ProblemResponses.forbidden("Invalid browser CSRF token.", "/api/v1/auth/browser/refresh");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getProperties()).containsEntry("code", "ACCESS_DENIED");
    }

    @Test
    void correlationIdPropagatesFromMdcWhenPresent() {
        // Kills the Negate mutant on (correlationId != null && !isBlank) and
        // the setProperty(correlationId) VoidMethodCall mutant.
        MDC.put("correlationId", "corr-123");
        try {
            var response = ProblemResponses.unauthorized("x", "/p");
            assertThat(response.getBody().getProperties()).containsEntry("correlationId", "corr-123");
        } finally {
            MDC.remove("correlationId");
        }
    }

    @Test
    void correlationIdAbsentWithoutMdc() {
        MDC.remove("correlationId");
        var response = ProblemResponses.unauthorized("x", "/p");
        assertThat(response.getBody().getProperties()).doesNotContainKey("correlationId");
    }

    @Test
    void timestampFollowsInjectedUtcClock() {
        ProblemResponses.setClockForTests(
                Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC));

        var response = ProblemResponses.unauthorized("x", "/p");

        assertThat(response.getBody().getProperties().get("timestamp").toString())
                .startsWith("2026-05-01T12:00");
        assertThat(LocalDateTime.now(Clock.systemUTC()).getYear()).isEqualTo(2026);
    }
}
