package com.bank.app.infrastructure.adapter.in.handler;

import com.bank.app.common.domain.exception.AuthorizationException;
import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.ConcurrentRequestException;
import com.bank.app.common.domain.exception.ErrorCode;
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
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
        return ProblemDetailFactory.create(status, ex.getErrorCode(), resolveBusinessMessage(ex), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationExceptions(MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return ProblemDetailFactory.createValidationError(errors, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        return ProblemDetailFactory.create(ErrorCode.INVALID_ARGUMENT, ex.getMessage(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        return ProblemDetailFactory.create(ErrorCode.AUTHENTICATION_FAILED, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        return ProblemDetailFactory.create(ErrorCode.ACCESS_DENIED, ex.getMessage(), request);
    }

    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<ProblemDetail> handleAuthorizationException(AuthorizationException ex, WebRequest request) {
        return ProblemDetailFactory.create(ErrorCode.ACCESS_DENIED, resolveBusinessMessage(ex), request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLockingFailureException(OptimisticLockingFailureException ex, WebRequest request) {
        String message = resolveMessage("error.optimistic_lock_conflict");
        return ProblemDetailFactory.create(ErrorCode.OPTIMISTIC_LOCK_CONFLICT, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex, WebRequest request) {
        String message = resolveMessage("error.invalid_format");
        ErrorCode code = ErrorCode.INVALID_FORMAT;
        if (ex.getCause() instanceof InvalidFormatException invalidFormatException) {
            if (invalidFormatException.getTargetType() != null && invalidFormatException.getTargetType().isEnum()) {
                message = resolveMessage("error.invalid_enum_value",
                        new Object[]{invalidFormatException.getValue(),
                                Arrays.toString(invalidFormatException.getTargetType().getEnumConstants())});
                code = ErrorCode.INVALID_ENUM_VALUE;
            }
        }
        return ProblemDetailFactory.create(code, message, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolationException(DataIntegrityViolationException ex, WebRequest request) {
        String message;
        ErrorCode code;
        if (ex.getCause() instanceof ConstraintViolationException) {
            message = resolveMessage("error.unique_constraint_violation");
            code = ErrorCode.UNIQUE_CONSTRAINT_VIOLATION;
        } else {
            message = resolveMessage("error.db_integrity_violation");
            code = ErrorCode.DB_INTEGRITY_VIOLATION;
        }
        return ProblemDetailFactory.create(code, message, request);
    }

    @ExceptionHandler(ConcurrentRequestException.class)
    public ResponseEntity<ProblemDetail> handleConcurrentRequestException(ConcurrentRequestException ex, WebRequest request) {
        return ProblemDetailFactory.create(HttpStatus.CONFLICT, ex.getErrorCode(), resolveBusinessMessage(ex), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex, WebRequest request) {
        String message = resolveMessage("error.unsupported_media_type",
                new Object[]{ex.getContentType()});
        return ProblemDetailFactory.create(ErrorCode.UNSUPPORTED_MEDIA_TYPE, message, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException ex, WebRequest request) {
        return ProblemDetailFactory.create(ErrorCode.METHOD_NOT_ALLOWED, ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResourceFoundException(NoResourceFoundException ex, WebRequest request) {
        return ProblemDetailFactory.create(ErrorCode.RESOURCE_NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneralException(Exception ex, WebRequest request) {
        log.error("Unexpected error occurred: ", ex);
        String message = resolveMessage("error.general_internal_error");
        return ProblemDetailFactory.create(ErrorCode.GENERAL_INTERNAL_ERROR, message, request);
    }
}
