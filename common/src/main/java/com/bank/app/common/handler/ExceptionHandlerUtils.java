package com.bank.app.common.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.time.LocalDateTime;

final class ExceptionHandlerUtils {

    private ExceptionHandlerUtils() {}

    static ResponseEntity<ProblemDetail> createProblemResponse(HttpStatus status, String code, String message, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, message);
        problemDetail.setTitle(status.getReasonPhrase());
        if (request != null) {
            try {
                String path = request.getDescription(false).replace("uri=", "");
                problemDetail.setInstance(URI.create(path));
            } catch (Exception ignored) {}
        }
        problemDetail.setProperty("code", code);
        problemDetail.setProperty("message", message);
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }
}
