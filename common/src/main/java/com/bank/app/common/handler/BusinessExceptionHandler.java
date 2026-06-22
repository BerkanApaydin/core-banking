package com.bank.app.common.handler;

import com.bank.app.common.exception.BusinessException;
import com.bank.app.common.exception.ConcurrentRequestException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class BusinessExceptionHandler {

    private final MessageSource messageSource;

    public BusinessExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(BusinessException ex, WebRequest request) {
        HttpStatus status = resolveHttpStatus(ex);
        String message = resolveMessage(ex.getMessageKey(), ex.getArgs());
        if (message.isEmpty() || message.equals(ex.getMessageKey())) {
            message = ex.getMessage() != null ? ex.getMessage() : "";
        }
        return ExceptionHandlerUtils.createProblemResponse(status, ex.getErrorCode(), message, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
        return ExceptionHandlerUtils.createProblemResponse(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage(), request);
    }

    @ExceptionHandler(ConcurrentRequestException.class)
    public ResponseEntity<ProblemDetail> handleConcurrentRequest(ConcurrentRequestException ex, WebRequest request) {
        String message = resolveMessage(ex.getMessageKey(), ex.getArgs());
        if (message.isEmpty() || message.equals(ex.getMessageKey())) {
            message = ex.getMessage() != null ? ex.getMessage() : "";
        }
        return ExceptionHandlerUtils.createProblemResponse(HttpStatus.CONFLICT, ex.getErrorCode(), message, request);
    }

    private String resolveMessage(String messageKey, Object[] args) {
        if (messageKey == null) return "";
        try {
            return messageSource.getMessage(messageKey, args, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            return messageKey;
        }
    }

    private static HttpStatus resolveHttpStatus(BusinessException ex) {
        String className = ex.getClass().getSimpleName();
        if (className.contains("NotFound")) return HttpStatus.NOT_FOUND;
        if (className.contains("Concurrent") || className.contains("Duplicate")) return HttpStatus.CONFLICT;
        if (className.contains("TooMany") || className.contains("RateLimit")) return HttpStatus.TOO_MANY_REQUESTS;
        return HttpStatus.BAD_REQUEST;
    }
}
