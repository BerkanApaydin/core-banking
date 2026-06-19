package com.bank.app.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
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

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    private String translate(BusinessException ex) {
        String messageKey = ex.getMessageKey();
        if (messageKey != null) {
            try {
                return messageSource.getMessage(messageKey, ex.getArgs(), LocaleContextHolder.getLocale());
            } catch (NoSuchMessageException e) {
                // fallback
            }
        }
        String message = ex.getMessage();
        return message != null ? message : "";
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        HttpStatus status = ex.getHttpStatus() != null ? ex.getHttpStatus() : HttpStatus.BAD_REQUEST;
        ErrorResponse response = ErrorResponse.of(status, translate(ex));
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
        ErrorResponse response = ErrorResponse.of(HttpStatus.BAD_REQUEST, ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        ErrorResponse response = ErrorResponse.of(HttpStatus.UNAUTHORIZED, ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        ErrorResponse response = ErrorResponse.of(HttpStatus.FORBIDDEN, ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailureException(OptimisticLockingFailureException ex) {
        ErrorResponse response = ErrorResponse.of(HttpStatus.CONFLICT, "İşlem sırasında eşzamanlı bir güncelleme çakışması oluştu. Lütfen tekrar deneyin.");
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        String message = "İstek formatı geçersiz.";
        if (ex.getCause() instanceof InvalidFormatException invalidFormatException) {
            if (invalidFormatException.getTargetType() != null && invalidFormatException.getTargetType().isEnum()) {
                message = "Geçersiz değer: " + invalidFormatException.getValue() + ". Kabul edilen değerler: " 
                        + Arrays.toString(invalidFormatException.getTargetType().getEnumConstants());
            }
        }
        ErrorResponse response = ErrorResponse.of(HttpStatus.BAD_REQUEST, message);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        String message = "Veritabanı bütünlük kısıt ihlali: İşlem yapılmak istenen veri zaten mevcut veya geçersiz.";
        if (ex.getCause() instanceof ConstraintViolationException) {
            message = "Bu kayıt zaten mevcut (benzersizlik kısıt ihlali).";
        }
        ErrorResponse response = ErrorResponse.of(HttpStatus.CONFLICT, message);
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ConcurrentRequestException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentRequestException(ConcurrentRequestException ex) {
        ErrorResponse response = ErrorResponse.of(HttpStatus.CONFLICT, translate(ex));
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex) {
        ErrorResponse response = ErrorResponse.of(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Desteklenmeyen medya türü: " + ex.getContentType());
        return new ResponseEntity<>(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        ErrorResponse response = ErrorResponse.of(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        log.error("Beklenmeyen bir hata oluştu: ", ex);
        ErrorResponse response = ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Sistemsel bir hata oluştu. Lütfen teknik destek ile iletişime geçin.");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public record ErrorResponse(int status, String message, LocalDateTime timestamp) {
        public static ErrorResponse of(HttpStatus status, String message) {
            return new ErrorResponse(status.value(), message, LocalDateTime.now());
        }
    }
}
