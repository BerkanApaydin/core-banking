package com.bank.app.user.adapter.in.web.support;

import com.bank.app.common.adapter.in.api.ProblemBody;
import java.net.URI;
import java.time.Clock;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * RFC 7807 error bodies for the user web adapters.
 *
 * <p>Thin renderer over the shared {@link ProblemBody} (same shape as the
 * infrastructure {@code ProblemDetailFactory}), but dependency-free: the web
 * layer must not depend on the {@code domain.exception} package
 * (see {@code LayeringArchitectureTest.controllersShouldNotContainBusinessLogic}),
 * so error codes are literals equal to the corresponding {@code ErrorCode} names.
 */
public final class ProblemResponses {

    private ProblemResponses() {}

    // Time-strategy seam (same pattern as ProblemDetailFactory): error-body
    // timestamps must be UTC, never server-zone. Static because this helper is
    // dependency-free by design (see class Javadoc); tests pin a fixed clock.
    // ThreadLocal so parallel test classes cannot pin each other's timestamps.
    private static final ThreadLocal<Clock> testClock = new ThreadLocal<>();

    /** Test-only clock injection. Null clears the calling thread's pin. */
    public static void setClockForTests(Clock clock) {
        if (clock == null) {
            testClock.remove();
        } else {
            testClock.set(clock);
        }
    }

    private static Clock clock() {
        Clock pinned = testClock.get();
        return pinned != null ? pinned : Clock.systemUTC();
    }

    public static ResponseEntity<ProblemDetail> unauthorized(String message, String path) {
        return problem(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", message, path);
    }

    public static ResponseEntity<ProblemDetail> forbidden(String message, String path) {
        return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED", message, path);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code,
            String message, String path) {
        ProblemBody body = ProblemBody.of(status.value(), code, message,
                clock(), MDC.get("correlationId"), path);
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        // No explicit setTitle: forStatusAndDetail already carries the reason
        // phrase, so a duplicate call would be an equivalent mutant.
        if (body.instance() != null) {
            try {
                detail.setInstance(URI.create(body.instance()));
            } catch (IllegalArgumentException ignored) {
                // Non-URI paths (tests, forwards) must not break the error body.
            }
        }
        detail.setProperty("code", body.code());
        detail.setProperty("message", body.message());
        detail.setProperty("timestamp", body.timestamp());
        if (body.correlationId() != null) {
            detail.setProperty("correlationId", body.correlationId());
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(detail);
    }
}
