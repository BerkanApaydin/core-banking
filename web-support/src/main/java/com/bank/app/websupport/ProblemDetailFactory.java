package com.bank.app.websupport;

import com.bank.app.common.adapter.in.api.ProblemBody;
import com.bank.app.common.domain.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.context.request.WebRequest;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Single RFC 7807 renderer shared by controllers, exception handlers and
 * servlet filters. All entry points render the same {@link ProblemBody}, so
 * field names, timestamp shape and problem type cannot drift between web
 * layers.
 */
public final class ProblemDetailFactory {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailFactory.class);

    // E-2: deterministic-time seam. Production keeps systemUTC; tests pin a
    // fixed clock so error-body timestamps are assertable. ThreadLocal (not
    // a shared volatile) so parallel test classes (surefire
    // parallel=classes, threadCount=4) cannot pin each other's timestamps;
    // production threads always fall back to systemUTC.
    private static final ThreadLocal<Clock> testClock = new ThreadLocal<>();

    private ProblemDetailFactory() {}

    /** Test/simulation clock injection (E-2). Null clears the calling thread's pin. */
    public static void setClockForTests(Clock clock) {
        if (clock == null) {
            testClock.remove();
        } else {
            testClock.set(clock);
        }
    }

    private static Clock currentClock() {
        Clock pinned = testClock.get();
        return pinned != null ? pinned : Clock.systemUTC();
    }

    /**
     * Renders a shared {@link ProblemBody} onto a Spring {@link ProblemDetail}.
     * Field names and timestamp shape are owned by the record; this method
     * only adapts them to the transport type.
     */
    private static void renderOnto(ProblemDetail problemDetail, ProblemBody body) {
        // No explicit setTitle: forStatusAndDetail already carries the reason phrase.
        // Semantic type (audit: previously about:blank): derived from the wire
        // code via ProblemBody.
        problemDetail.setType(URI.create(body.problemType()));
        if (body.instance() != null) {
            try {
                problemDetail.setInstance(URI.create(body.instance()));
            } catch (Exception e) {
                log.trace("Failed to set request URI in ProblemDetail", e);
            }
        }
        problemDetail.setProperty("code", body.code());
        problemDetail.setProperty("message", body.message());
        // ISO-8601 string (not LocalDateTime) so every writer works with any
        // ObjectMapper, including ones without the JSR-310 module.
        problemDetail.setProperty("timestamp", body.timestamp());
        // CorrelationIdFilter puts the id into MDC before every other filter
        // and echoes it as a response header. Mirroring it in the body lets
        // clients include it in bug reports without reading headers, and gives
        // operators a join key between the error payload and the JSON logs.
        // Absent outside a request thread (e.g. unit tests without the filter):
        // omit rather than emitting null.
        if (body.correlationId() != null) {
            problemDetail.setProperty("correlationId", body.correlationId());
        }
    }

    private static String instanceFromRequest(@Nullable WebRequest request) {
        if (request == null) return null;
        try {
            // WebRequest#getDescription(false) yields "uri=/path".
            return request.getDescription(false).replace("uri=", "");
        } catch (Exception e) {
            log.trace("Failed to read request description for ProblemDetail", e);
            return null;
        }
    }

    public static ResponseEntity<ProblemDetail> create(ErrorCode code, String message, WebRequest request) {
        HttpStatus status = BusinessErrorHttpMapper.toStatus(code);
        return createResponse(status, code.code(), message, instanceFromRequest(request));
    }

    public static ResponseEntity<ProblemDetail> create(HttpStatus status, String errorCode, String message, WebRequest request) {
        return createResponse(status, errorCode, message, instanceFromRequest(request));
    }

    public static ResponseEntity<ProblemDetail> create(HttpStatus status, String errorCode, String message,
            @Nullable String path) {
        return createResponse(status, errorCode, message, path);
    }

    public static ResponseEntity<ProblemDetail> unauthorized(String message, @Nullable String path) {
        return create(HttpStatus.UNAUTHORIZED, ErrorCode.AUTHENTICATION_FAILED.code(), message, path);
    }

    public static ResponseEntity<ProblemDetail> forbidden(String message, @Nullable String path) {
        return create(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED.code(), message, path);
    }

    /**
     * R-U10/R-U11: a security backend (database, revocation store) failed.
     * 503 tells the client to retry; unlike a 401 it must not destroy the
     * browser session cookies, because the session was never proven invalid.
     */
    public static ResponseEntity<ProblemDetail> serviceUnavailable(String message, @Nullable String path) {
        return create(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SECURITY_BACKEND_UNAVAILABLE.code(), message, path);
    }

    public static ResponseEntity<ProblemDetail> createValidationError(Map<String, String> fieldErrors, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problemDetail.setTitle("Validation Failed");
        renderOnto(problemDetail, ProblemBody.of(HttpStatus.BAD_REQUEST.value(),
                ErrorCode.VALIDATION_FAILED.code(), "Validation failed",
                currentClock(), MDC.get("correlationId"), instanceFromRequest(request)));
        problemDetail.setProperty("errors", new HashMap<>(fieldErrors));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_PROBLEM_JSON))
                .body(problemDetail);
    }

    /**
     * Writes an RFC 7807 problem response directly to a servlet response. Used by filters
     * and entry points that run outside {@code @RestControllerAdvice} handling so that
     * every error body shares the same shape.
     */
    public static void writeProblem(HttpServletResponse response, ObjectMapper objectMapper,
            HttpStatus status, String code, String message, @Nullable String path) throws IOException {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, message);
        // No explicit setTitle: forStatusAndDetail already carries the reason
        // phrase (pinned by title assertions below).
        renderOnto(problemDetail, ProblemBody.of(status.value(), code, message,
                currentClock(), MDC.get("correlationId"), path));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problemDetail);
    }

    private static ResponseEntity<ProblemDetail> createResponse(HttpStatus status, String code, String message,
            @Nullable String path) {
        if (status == null) {
            throw new IllegalArgumentException("HttpStatus must not be null");
        }
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, message);
        renderOnto(problemDetail, ProblemBody.of(status.value(), code, message,
                currentClock(), MDC.get("correlationId"), path));
        return ResponseEntity.status(status)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_PROBLEM_JSON))
                .body(problemDetail);
    }
}
