package com.bank.app.infrastructure.adapter.in.handler;

import com.bank.app.common.domain.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class ProblemDetailFactory {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailFactory.class);

    private ProblemDetailFactory() {}

    public static ResponseEntity<ProblemDetail> create(ErrorCode code, String message, WebRequest request) {
        HttpStatus status = HttpStatus.resolve(code.getHttpStatus());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
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
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_PROBLEM_JSON))
                .body(problemDetail);
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
        return ResponseEntity.status(status)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_PROBLEM_JSON))
                .body(problemDetail);
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
