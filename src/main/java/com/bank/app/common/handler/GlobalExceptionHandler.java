package com.bank.app.common.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import java.util.Arrays;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bank.app.common.exception.BusinessException;
import com.bank.app.common.exception.ConcurrentRequestException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    private String resolveMessage(String messageKey, Object[] args) {
        if (messageKey == null) {
            return "";
        }
        try {
            return messageSource.getMessage(messageKey, args, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            return messageKey;
        }
    }

    private String resolveMessage(String messageKey) {
        return resolveMessage(messageKey, null);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        HttpStatus status = ex.getHttpStatus() != null ? ex.getHttpStatus() : HttpStatus.BAD_REQUEST;
        String message = resolveMessage(ex.getMessageKey(), ex.getArgs());
        if (message.isEmpty() || message.equals(ex.getMessageKey())) {
            message = ex.getMessage() != null ? ex.getMessage() : "";
        }
        ErrorResponse response = ErrorResponse.of(status, ex.getErrorCode(), message);
        return new ResponseEntity<>(response, status);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        ErrorResponse response = ErrorResponse.of(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        ErrorResponse response = ErrorResponse.of(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        ErrorResponse response = ErrorResponse.of(HttpStatus.FORBIDDEN, "ACCESS_DENIED", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailureException(OptimisticLockingFailureException ex) {
        String message = resolveMessage("error.optimistic_lock_conflict");
        ErrorResponse response = ErrorResponse.of(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT", message);
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
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
        ErrorResponse response = ErrorResponse.of(HttpStatus.BAD_REQUEST, code, message);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        String message;
        String code;
        if (ex.getCause() instanceof ConstraintViolationException) {
            message = resolveMessage("error.unique_constraint_violation");
            code = "UNIQUE_CONSTRAINT_VIOLATION";
        } else {
            message = resolveMessage("error.db_integrity_violation");
            code = "DB_INTEGRITY_VIOLATION";
        }
        ErrorResponse response = ErrorResponse.of(HttpStatus.CONFLICT, code, message);
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ConcurrentRequestException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentRequestException(ConcurrentRequestException ex) {
        String message = resolveMessage(ex.getMessageKey(), ex.getArgs());
        if (message.isEmpty() || message.equals(ex.getMessageKey())) {
            message = ex.getMessage() != null ? ex.getMessage() : "";
        }
        ErrorResponse response = ErrorResponse.of(HttpStatus.CONFLICT, ex.getErrorCode(), message);
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex) {
        String message = resolveMessage("error.unsupported_media_type",
                new Object[]{ex.getContentType()});
        ErrorResponse response = ErrorResponse.of(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", message);
        return new ResponseEntity<>(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        ErrorResponse response = ErrorResponse.of(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        log.error("Beklenmeyen bir hata oluştu: ", ex);
        String message = resolveMessage("error.general_internal_error");
        ErrorResponse response = ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", message);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public record ErrorResponse(int status, String code, String message, LocalDateTime timestamp) {
        public static ErrorResponse of(HttpStatus status, String code, String message) {
            return new ErrorResponse(status.value(), code, message, LocalDateTime.now());
        }
    }
}
