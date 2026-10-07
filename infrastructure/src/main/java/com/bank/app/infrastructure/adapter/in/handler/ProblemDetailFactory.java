package com.bank.app.infrastructure.adapter.in.handler;

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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class ProblemDetailFactory {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailFactory.class);

    private ProblemDetailFactory() {}

    public static ResponseEntity<ProblemDetail> create(ErrorCode code, String message, WebRequest request) {
        HttpStatus status = BusinessErrorHttpMapper.toStatus(code);
        return createResponse(status, code.code(), message, request);
    }

    public static ResponseEntity<ProblemDetail> create(HttpStatus status, String errorCode, String message, WebRequest request) {
        return createResponse(status, errorCode, message, request);
    }

    public static ResponseEntity<ProblemDetail> createValidationError(Map<String, String> fieldErrors, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problemDetail.setTitle("Validation Failed");
        setInstanceFromRequest(problemDetail, request);
        problemDetail.setProperty("code", ErrorCode.VALIDATION_FAILED.code());
        problemDetail.setProperty("message", "Validation failed");
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        problemDetail.setProperty("errors", new HashMap<>(fieldErrors));
        setCorrelationId(problemDetail);
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
        problemDetail.setTitle(status.getReasonPhrase());
        if (path != null) {
            try {
                problemDetail.setInstance(URI.create(path));
            } catch (Exception e) {
                log.trace("Failed to set request URI in ProblemDetail", e);
            }
        }
        problemDetail.setProperty("code", code);
        problemDetail.setProperty("message", message);
        // ISO-8601 string (not LocalDateTime) so this writer works with any ObjectMapper,
        // including ones without the JSR-310 module. Same shape Spring Boot renders by default.
        problemDetail.setProperty("timestamp", LocalDateTime.now().toString());
        setCorrelationId(problemDetail);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problemDetail);
    }

    private static ResponseEntity<ProblemDetail> createResponse(HttpStatus status, String code, String message, WebRequest request) {
        if (status == null) {
            throw new IllegalArgumentException("HttpStatus must not be null");
        }
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, message);
        problemDetail.setTitle(status.getReasonPhrase());
        setInstanceFromRequest(problemDetail, request);
        problemDetail.setProperty("code", code);
        problemDetail.setProperty("message", message);
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        setCorrelationId(problemDetail);
        return ResponseEntity.status(status)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_PROBLEM_JSON))
                .body(problemDetail);
    }

    private static void setCorrelationId(ProblemDetail problemDetail) {
        // CorrelationIdFilter puts the id into MDC before every other filter
        // and echoes it as a response header. Mirroring it in the body lets
        // clients include it in bug reports without reading headers, and gives
        // operators a join key between the error payload and the JSON logs.
        // Absent outside a request thread (e.g. unit tests without the filter):
        // omit rather than emitting null.
        String correlationId = MDC.get("correlationId");
        if (correlationId != null && !correlationId.isBlank()) {
            problemDetail.setProperty("correlationId", correlationId);
        }
    }

    private static void setInstanceFromRequest(ProblemDetail problemDetail, @Nullable WebRequest request) {
        if (request == null) return;
        try {
            String path = request.getDescription(false).replace("uri=", "");
            problemDetail.setInstance(URI.create(path));
        } catch (Exception e) {
            log.trace("Failed to set request URI in ProblemDetail", e);
        }
    }
}
