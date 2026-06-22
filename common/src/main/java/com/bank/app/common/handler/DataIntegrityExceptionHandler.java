package com.bank.app.common.handler;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

@RestControllerAdvice
public class DataIntegrityExceptionHandler {

    private final MessageSource messageSource;

    public DataIntegrityExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLocking(OptimisticLockingFailureException ex, WebRequest request) {
        String message = resolveMessage("error.optimistic_lock_conflict");
        return ExceptionHandlerUtils.createProblemResponse(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT", message, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        String message;
        String code;
        if (ex.getCause() instanceof ConstraintViolationException) {
            message = resolveMessage("error.unique_constraint_violation");
            code = "UNIQUE_CONSTRAINT_VIOLATION";
        } else {
            message = resolveMessage("error.db_integrity_violation");
            code = "DB_INTEGRITY_VIOLATION";
        }
        return ExceptionHandlerUtils.createProblemResponse(HttpStatus.CONFLICT, code, message, request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, WebRequest request) {
        String message = resolveMessage("error.unsupported_media_type", new Object[]{ex.getContentType()});
        return ExceptionHandlerUtils.createProblemResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", message, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, WebRequest request) {
        return ExceptionHandlerUtils.createProblemResponse(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage(), request);
    }

    private String resolveMessage(String messageKey, Object[]... args) {
        if (messageKey == null) return "";
        try {
            Object[] resolved = args.length > 0 ? args[0] : null;
            return messageSource.getMessage(messageKey, resolved, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            return messageKey;
        }
    }
}
