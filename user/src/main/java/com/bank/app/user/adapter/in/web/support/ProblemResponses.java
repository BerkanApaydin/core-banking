package com.bank.app.user.adapter.in.web.support;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * RFC 7807 error bodies for the user web adapters.
 *
 * <p>Same {@code code/message/timestamp/correlationId} shape as
 * {@code ProblemDetailFactory} (infrastructure), but dependency-free: the web
 * layer must not depend on the {@code domain.exception} package
 * (see {@code LayeringArchitectureTest.controllersShouldNotContainBusinessLogic}),
 * so error codes are literals equal to the corresponding {@code ErrorCode} names.
 */
public final class ProblemResponses {

    private ProblemResponses() {}

    // Time-strategy seam (same pattern as ProblemDetailFactory): error-body
    // timestamps must be UTC, never server-zone. Static because this helper is
    // dependency-free by design (see class Javadoc); tests pin a fixed clock.
    private static volatile Clock clock = Clock.systemUTC();

    /** Test-only clock injection. */
    public static void setClockForTests(Clock testClock) {
        clock = testClock != null ? testClock : Clock.systemUTC();
    }

    public static ResponseEntity<ProblemDetail> unauthorized(String message, String path) {
        return problem(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", message, path);
    }

    public static ResponseEntity<ProblemDetail> forbidden(String message, String path) {
        return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED", message, path);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code,
            String message, String path) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        // No explicit setTitle: forStatusAndDetail already carries the reason
        // phrase, so a duplicate call would be an equivalent mutant.
        if (path != null) {
            try {
                detail.setInstance(URI.create(path));
            } catch (IllegalArgumentException ignored) {
                // Non-URI paths (tests, forwards) must not break the error body.
            }
        }
        detail.setProperty("code", code);
        detail.setProperty("message", message);
        detail.setProperty("timestamp", LocalDateTime.now(clock).toString());
        String correlationId = MDC.get("correlationId");
        if (correlationId != null && !correlationId.isBlank()) {
            detail.setProperty("correlationId", correlationId);
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(detail);
    }
}
