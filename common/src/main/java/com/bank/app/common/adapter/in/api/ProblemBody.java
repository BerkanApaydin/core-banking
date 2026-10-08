package com.bank.app.common.adapter.in.api;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Framework-free RFC 7807 error body.
 *
 * <p>Single owner of the problem shape ({@code status/code/message/timestamp/
 * correlationId/instance}) shared by the infrastructure
 * {@code ProblemDetailFactory} and the user-module {@code ProblemResponses}:
 * both are thin renderers over this record, so the two can never silently
 * drift (timestamp type, missing correlationId). Transport types
 * ({@code ProblemDetail}, servlet writer) stay in the renderers; this record
 * carries only JDK types. Timestamps are UTC ISO-8601 strings (never
 * server-zone), matching what the renderers previously emitted on the wire.
 */
public record ProblemBody(
        int status,
        String code,
        String message,
        String timestamp,
        String correlationId,
        String instance) {

    public ProblemBody {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    /**
     * @param clock rendering clock (callers pass their test-seam or systemUTC)
     * @param message null (unresolvable bundle key in tests) degrades to ""
     * @param correlationId blank values normalize to null (field omitted)
     * @param instance request path or null (field omitted)
     */
    public static ProblemBody of(int status, String code, String message, Clock clock,
            String correlationId, String instance) {
        Objects.requireNonNull(clock, "clock must not be null");
        return new ProblemBody(status, code, message == null ? "" : message,
                LocalDateTime.now(clock).toString(),
                blankToNull(correlationId), instance);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
