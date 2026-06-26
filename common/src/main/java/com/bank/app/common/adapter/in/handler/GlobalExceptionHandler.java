package com.bank.app.common.adapter.in.handler;

import com.bank.app.common.domain.exception.AuthorizationException;
import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.ConcurrentRequestException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    private String resolveMessage(String messageKey, @Nullable Object[] args) {
        if (messageKey == null) return "";
        try {
            return messageSource.getMessage(messageKey, args, LocaleContextHolder.getLocale());
        } catch (NoSuchMessageException e) {
            log.trace("Message not found for key: {}", messageKey, e);
            return messageKey;
        }
    }

    private String resolveMessage(String messageKey) {
        return resolveMessage(messageKey, null);
    }

    private ResponseEntity<ProblemDetail> createProblemResponse(HttpStatus status, String code, String message, WebRequest request) {
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

    private static void setInstanceFromRequest(ProblemDetail problemDetail, WebRequest request) {
        if (request == null) return;
        try {
            String path = request.getDescription(false).replace("uri=", "");
            problemDetail.setInstance(URI.create(path));
        } catch (Exception e) {
            log.trace("Failed to set request URI in ProblemDetail", e);
        }
    }

    private String resolveBusinessMessage(BusinessException ex) {
        String message = resolveMessage(ex.getMessageKey(), ex.getArgs());
        if (message.isEmpty() || message.equals(ex.getMessageKey())) {
            message = ex.getMessage() != null ? ex.getMessage() : "";
        }
        return message;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(BusinessException ex, WebRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getHttpStatusCode());
        if (status == null) status = HttpStatus.BAD_REQUEST;
        return createProblemResponse(status, ex.getErrorCode(), resolveBusinessMessage(ex), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationExceptions(MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problemDetail.setTitle("Validation Failed");
        setInstanceFromRequest(problemDetail, request);
        problemDetail.setProperty("code", "VALIDATION_FAILED");
        problemDetail.setProperty("message", "Validation failed");
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        problemDetail.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_PROBLEM_JSON))
                .body(problemDetail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        return createProblemResponse(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        return createProblemResponse(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        return createProblemResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED", ex.getMessage(), request);
    }

    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<ProblemDetail> handleAuthorizationException(AuthorizationException ex, WebRequest request) {
        return createProblemResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED", resolveBusinessMessage(ex), request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLockingFailureException(OptimisticLockingFailureException ex, WebRequest request) {
        String message = resolveMessage("error.optimistic_lock_conflict");
        return createProblemResponse(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT", message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex, WebRequest request) {
        String message = resolveMessage("error.invalid_format");
        String code = "INVALID_FORMAT";
        if (ex.getCause() instanceof InvalidFormatException invalidFormatException) {
            if (invalidFormatException.getTargetType() != null && invalidFormatException.getTargetType().isEnum()) {
                message = resolveMessage("error.invalid_enum_value",
                        new Object[]{invalidFormatException.getValue(),
                                Arrays.toString(invalidFormatException.getTargetType().getEnumConstants())});
                code = "INVALID_ENUM_VALUE";
            }
        }
        return createProblemResponse(HttpStatus.BAD_REQUEST, code, message, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolationException(DataIntegrityViolationException ex, WebRequest request) {
        String message;
        String code;
        if (ex.getCause() instanceof ConstraintViolationException) {
            message = resolveMessage("error.unique_constraint_violation");
            code = "UNIQUE_CONSTRAINT_VIOLATION";
        } else {
            message = resolveMessage("error.db_integrity_violation");
            code = "DB_INTEGRITY_VIOLATION";
        }
        return createProblemResponse(HttpStatus.CONFLICT, code, message, request);
    }

    @ExceptionHandler(ConcurrentRequestException.class)
    public ResponseEntity<ProblemDetail> handleConcurrentRequestException(ConcurrentRequestException ex, WebRequest request) {
        return createProblemResponse(HttpStatus.CONFLICT, ex.getErrorCode(), resolveBusinessMessage(ex), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex, WebRequest request) {
        String message = resolveMessage("error.unsupported_media_type",
                new Object[]{ex.getContentType()});
        return createProblemResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", message, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException ex, WebRequest request) {
        return createProblemResponse(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResourceFoundException(NoResourceFoundException ex, WebRequest request) {
        return createProblemResponse(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneralException(Exception ex, WebRequest request) {
        log.error("Unexpected error occurred: ", ex);
        String message = resolveMessage("error.general_internal_error");
        return createProblemResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", message, request);
    }
}
