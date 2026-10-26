package com.bank.app.websupport;

import com.bank.app.common.adapter.in.api.ProblemBody;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemDetailFactoryHelpersTest {

    @AfterEach
    void resetClock() {
        ProblemDetailFactory.setClockForTests(null);
        MDC.remove("correlationId");
    }

    @Test
    void unauthorizedRendersProblemShape() {
        var response = ProblemDetailFactory.unauthorized(
                "No active browser session.", "/api/v1/auth/browser/session");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType().toString())
                .contains("application/problem+json");
        assertThat(response.getBody().getProperties())
                .containsEntry("code", "AUTHENTICATION_FAILED")
                .containsEntry("message", "No active browser session.")
                .containsKey("timestamp");
        assertThat(response.getBody().getTitle()).isEqualTo("Unauthorized");
        assertThat(response.getBody().getInstance()).isNotNull();
        assertThat(response.getBody().getInstance().toString())
                .isEqualTo("/api/v1/auth/browser/session");
        assertThat(response.getBody().getType()).isEqualTo(URI.create(
                ProblemBody.PROBLEM_TYPE_BASE + "authentication_failed"));
    }

    @Test
    void nullPathOmitsInstance() {
        var response = ProblemDetailFactory.unauthorized("x", null);

        assertThat(response.getBody().getInstance()).isNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Unauthorized");
    }

    @Test
    void forbiddenRendersProblemShape() {
        var response = ProblemDetailFactory.forbidden("Denied.", "/p");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getTitle()).isEqualTo("Forbidden");
        assertThat(response.getBody().getInstance().toString()).isEqualTo("/p");
        assertThat(response.getBody().getProperties()).containsEntry("code", "ACCESS_DENIED");
    }

    @Test
    void serviceUnavailableRendersProblemShape() {
        var response = ProblemDetailFactory.serviceUnavailable(
                "Security service temporarily unavailable.", "/api/v1/auth/browser/logout");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getTitle()).isEqualTo("Service Unavailable");
        assertThat(response.getBody().getInstance().toString())
                .isEqualTo("/api/v1/auth/browser/logout");
        assertThat(response.getBody().getProperties())
                .containsEntry("code", "SECURITY_BACKEND_UNAVAILABLE")
                .containsEntry("message", "Security service temporarily unavailable.");
    }

    @Test
    void correlationIdPropagatesFromMdcWhenPresent() {
        MDC.put("correlationId", "corr-123");

        var response = ProblemDetailFactory.unauthorized("x", "/p");

        assertThat(response.getBody().getProperties()).containsEntry("correlationId", "corr-123");
    }

    @Test
    void correlationIdAbsentWithoutMdc() {
        var response = ProblemDetailFactory.unauthorized("x", "/p");

        assertThat(response.getBody().getProperties()).doesNotContainKey("correlationId");
    }

    @Test
    void timestampFollowsInjectedUtcClock() {
        ProblemDetailFactory.setClockForTests(
                Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC));

        var response = ProblemDetailFactory.unauthorized("x", "/p");

        assertThat(response.getBody().getProperties().get("timestamp").toString())
                .startsWith("2026-05-01T12:00");
    }

    @Test
    void clearingThePinnedClockRestoresTheSystemClock() {
        ProblemDetailFactory.setClockForTests(
                Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC));
        ProblemDetailFactory.setClockForTests(null);

        var response = ProblemDetailFactory.unauthorized("x", "/p");

        assertThat(response.getBody().getProperties().get("timestamp").toString())
                .doesNotStartWith("2026-05-01T12:00");
    }
}
