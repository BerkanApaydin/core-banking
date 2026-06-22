package com.bank.app.common.handler;

import com.bank.app.common.exception.AuthorizationException;
import com.bank.app.common.exception.BusinessException;
import com.bank.app.common.exception.ConcurrentRequestException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
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

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    private String resolveMessage(String messageKey, Object[] args) {
        if (messageKey == null) return "";
        try {
            return messageSource.getMessage(messageKey, args, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            return messageKey;
        }
    }

    private String resolveMessage(String messageKey) {
        return resolveMessage(messageKey, null);
    }

    private ResponseEntity<ProblemDetail> createProblemResponse(HttpStatus status, String code, String message, WebRequest request) {
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
                .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    private static HttpStatus resolveHttpStatus(BusinessException ex) {
        String className = ex.getClass().getSimpleName();
        if (className.contains("NotFound")) return HttpStatus.NOT_FOUND;
        if (className.contains("Concurrent") || className.contains("Duplicate")) return HttpStatus.CONFLICT;
        if (className.contains("TooMany") || className.contains("RateLimit")) return HttpStatus.TOO_MANY_REQUESTS;
        return HttpStatus.BAD_REQUEST;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(BusinessException ex, WebRequest request) {
        HttpStatus status = resolveHttpStatus(ex);
        String message = resolveMessage(ex.getMessageKey(), ex.getArgs());
        if (message.isEmpty() || message.equals(ex.getMessageKey())) {
            message = ex.getMessage() != null ? ex.getMessage() : "";
        }
        return createProblemResponse(status, ex.getErrorCode(), message, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationExceptions(MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problemDetail.setTitle("Validation Failed");
        if (request != null) {
            try {
                problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
            } catch (Exception ignored) {}
        }
        problemDetail.setProperty("code", "VALIDATION_FAILED");
        problemDetail.setProperty("message", "Validation failed");
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        problemDetail.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
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
        String message = resolveMessage(ex.getMessageKey(), ex.getArgs());
        if (message.isEmpty() || message.equals(ex.getMessageKey())) {
            message = ex.getMessage() != null ? ex.getMessage() : "";
        }
        return createProblemResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED", message, request);
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
        String message = resolveMessage(ex.getMessageKey(), ex.getArgs());
        if (message.isEmpty() || message.equals(ex.getMessageKey())) {
            message = ex.getMessage() != null ? ex.getMessage() : "";
        }
        return createProblemResponse(HttpStatus.CONFLICT, ex.getErrorCode(), message, request);
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
        log.error("Beklenmeyen bir hata oluştu: ", ex);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Beklenmeyen bir hata oluştu.");
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setProperty("code", "INTERNAL_ERROR");
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        if (request != null) {
            try {
                problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
            } catch (Exception ignored) {}
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }
}
