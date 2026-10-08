package com.bank.app.infrastructure.adapter.in.handler;

import com.bank.app.common.domain.exception.ErrorCode;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Malformed-request failures: bean validation, missing headers/parameters,
 * type mismatches, unreadable bodies, unsupported media types/methods and
 * unknown paths. Fixed catalog messages everywhere: request methods, paths
 * and content types are never reflected, so 4xx bodies cannot be used for
 * probing.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RequestProblemHandler {

    private static final Logger log = LoggerFactory.getLogger(RequestProblemHandler.class);

    private final ProblemMessageResolver messages;

    public RequestProblemHandler(ProblemMessageResolver messages) {
        this.messages = messages;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationExceptions(MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return ProblemDetailFactory.createValidationError(errors, request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingRequestHeader(
            MissingRequestHeaderException ex, WebRequest request) {
        // Missing auth material (e.g. Authorization on logout) means the caller
        // is unauthenticated — 401, never the 500 fallback. The header name is
        // returned; the header value is never echoed.
        log.warn("Missing required header: {}", ex.getHeaderName());
        String message = messages.resolveOrDefault("error.authentication_failed", "Authentication failed.");
        return ProblemDetailFactory.create(ErrorCode.AUTHENTICATION_FAILED, message, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingRequestParameter(
            MissingServletRequestParameterException ex, WebRequest request) {
        return ProblemDetailFactory.createValidationError(
                Map.of(ex.getParameterName(), "Required parameter is missing"), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex, WebRequest request) {
        return ProblemDetailFactory.createValidationError(
                Map.of(ex.getName(), "Invalid parameter value"), request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleMethodValidation(
            HandlerMethodValidationException ex, WebRequest request) {
        return ProblemDetailFactory.createValidationError(
                Map.of("request", "Invalid request parameters"), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolationException(
            ConstraintViolationException ex, WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations()
                .forEach(violation -> {
                    String path = violation.getPropertyPath() != null
                            ? violation.getPropertyPath().toString() : "parameter";
                    String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
                    errors.put(field, violation.getMessage());
                });
        return ProblemDetailFactory.createValidationError(errors, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        // Never echo raw exception messages: they may carry SQL fragments,
        // paths, or validation internals. Log the detail, return a generic key.
        log.warn("Invalid argument rejected: {}", ex.getClass().getSimpleName());
        String message = messages.resolveOrDefault("error.invalid_argument", "Invalid request argument.");
        return ProblemDetailFactory.create(ErrorCode.INVALID_ARGUMENT, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex, WebRequest request) {
        String message = messages.resolveMessage("error.invalid_format");
        ErrorCode code = ErrorCode.INVALID_FORMAT;
        if (ex.getCause() instanceof InvalidFormatException invalidFormatException) {
            if (invalidFormatException.getTargetType() != null && invalidFormatException.getTargetType().isEnum()) {
                // ERR-02: never reflect the rejected value to the wire (probing
                // oracle) — only the server-defined accepted-values list is
                // safe to render. The rejected value goes to the log only.
                log.warn("Invalid enum value rejected for {}",
                        invalidFormatException.getTargetType().getSimpleName());
                message = messages.resolveMessage("error.invalid_enum_value",
                        new Object[]{Arrays.toString(
                                invalidFormatException.getTargetType().getEnumConstants())});
                code = ErrorCode.INVALID_ENUM_VALUE;
            }
        }
        return ProblemDetailFactory.create(code, message, request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex, WebRequest request) {
        String message = messages.resolveMessage("error.unsupported_media_type",
                new Object[]{ex.getContentType()});
        return ProblemDetailFactory.create(ErrorCode.UNSUPPORTED_MEDIA_TYPE, message, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException ex, WebRequest request) {
        // D12/K13: never echo ex.getMessage() — it reflects the request
        // method and supported methods. Fixed catalog message instead.
        log.warn("Method not supported: {}", ex.getMethod());
        String message = messages.resolveOrDefault("error.method_not_allowed",
                "Request method is not supported for this endpoint.");
        return ProblemDetailFactory.create(ErrorCode.METHOD_NOT_ALLOWED, message, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResourceFoundException(NoResourceFoundException ex, WebRequest request) {
        // D12/K13: never echo ex.getMessage() — it reflects the request path
        // (reflected content). Fixed catalog message instead.
        log.warn("No resource found for HTTP method: {}", ex.getHttpMethod());
        String message = messages.resolveOrDefault("error.resource_not_found",
                "The requested resource was not found.");
        return ProblemDetailFactory.create(ErrorCode.RESOURCE_NOT_FOUND, message, request);
    }
}
