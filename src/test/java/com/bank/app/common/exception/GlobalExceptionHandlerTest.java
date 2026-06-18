package com.bank.app.common.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @Mock
    private MessageSource messageSource;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(messageSource);
    }

    @Test
    void shouldHandleNotFoundExceptions() {
        BusinessException ex = new AccountNotFoundException("TR1");
        when(messageSource.getMessage(eq(ex.getMessageKey()), any(), any(Locale.class)))
                .thenReturn("Account not found TR1");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleNotFoundExceptions(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Account not found TR1", response.getBody().message());
    }

    @Test
    void shouldHandleNotFoundExceptionsFallback() {
        BusinessException ex = new AccountNotFoundException("TR1");
        when(messageSource.getMessage(eq(ex.getMessageKey()), any(), any(Locale.class)))
                .thenThrow(new NoSuchMessageException("No key"));

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleNotFoundExceptions(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ex.getMessage(), response.getBody().message());
    }

    @Test
    void shouldHandleBusinessException() {
        BusinessException ex = new InsufficientBalanceException("error.insufficient_balance",
                new Object[] { "TR1", BigDecimal.TEN }, "Insufficient balance");
        when(messageSource.getMessage(eq(ex.getMessageKey()), any(), any(Locale.class)))
                .thenReturn("Insufficient balance");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleBusinessException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Insufficient balance", response.getBody().message());
    }

    @Test
    void shouldHandleValidationExceptions() {
        BindingResult bindingResult = new MapBindingResult(new HashMap<>(), "request");
        bindingResult.addError(new FieldError("request", "amount", "Amount must be positive"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<Map<String, String>> response = handler.handleValidationExceptions(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Amount must be positive", response.getBody().get("amount"));
    }

    @Test
    void shouldHandleIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleIllegalArgumentException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid argument", response.getBody().message());
    }

    @Test
    void shouldHandleAuthenticationException() {
        AuthenticationException ex = new AuthenticationException("Bad credentials") {
        };

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleAuthenticationException(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Bad credentials", response.getBody().message());
    }

    @Test
    void shouldHandleTooManyFailedLoginAttemptsException() {
        TooManyFailedLoginAttemptsException ex = new TooManyFailedLoginAttemptsException("Çok fazla başarısız giriş denemesi");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler
                .handleTooManyFailedLoginAttemptsException(ex);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Çok fazla başarısız giriş denemesi", response.getBody().message());
    }

    @Test
    void shouldHandleAccessDeniedException() {
        AccessDeniedException ex = new AccessDeniedException("Access denied");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleAccessDeniedException(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Access denied", response.getBody().message());
    }

    @Test
    void shouldHandleOptimisticLockingFailureException() {
        OptimisticLockingFailureException ex = new OptimisticLockingFailureException("Conflict");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler
                .handleOptimisticLockingFailureException(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("İşlem sırasında eşzamanlı bir güncelleme çakışması oluştu. Lütfen tekrar deneyin.",
                response.getBody().message());
    }

    @Test
    void shouldHandleHttpMessageNotReadableException() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Not readable");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler
                .handleHttpMessageNotReadableException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("İstek formatı geçersiz.", response.getBody().message());
    }

    enum DummyEnum {
        VALUE1, VALUE2
    }

    @Test
    void shouldHandleHttpMessageNotReadableExceptionWithEnumCause() {
        InvalidFormatException cause = new InvalidFormatException(null, "Invalid value", "VALUE3", DummyEnum.class);
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Not readable", cause, null);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler
                .handleHttpMessageNotReadableException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().message().contains("Geçersiz değer: VALUE3"));
        assertTrue(response.getBody().message().contains("Kabul edilen değerler"));
    }

    @Test
    void shouldHandleHttpMessageNotReadableExceptionWithNonEnumCause() {
        InvalidFormatException cause = new InvalidFormatException(null, "Invalid value", "123", Integer.class);
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Not readable", cause, null);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler
                .handleHttpMessageNotReadableException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("İstek formatı geçersiz.", response.getBody().message());
    }

    @Test
    void shouldHandleHttpMessageNotReadableExceptionWithNullTargetTypeCause() {
        InvalidFormatException cause = new InvalidFormatException(null, "Invalid value", "123", null);
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Not readable", cause, null);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler
                .handleHttpMessageNotReadableException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("İstek formatı geçersiz.", response.getBody().message());
    }

    @Test
    void shouldTranslateWithNullMessage() {
        BusinessException ex = mock(BusinessException.class);
        when(ex.getMessageKey()).thenReturn(null);
        when(ex.getMessage()).thenReturn(null);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleBusinessException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("", response.getBody().message());
    }

    @Test
    void shouldHandleDataIntegrityViolationException() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("Violation");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler
                .handleDataIntegrityViolationException(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Veritabanı bütünlük kısıt ihlali: İşlem yapılmak istenen veri zaten mevcut veya geçersiz.",
                response.getBody().message());
    }

    @Test
    void shouldHandleDataIntegrityViolationExceptionWithConstraintCause() {
        ConstraintViolationException cause = new ConstraintViolationException("Constraint fail", null, "uk_name");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("Violation", cause);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler
                .handleDataIntegrityViolationException(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Bu kayıt zaten mevcut (benzersizlik kısıt ihlali).",
                response.getBody().message());
    }

    @Test
    void shouldHandleConcurrentRequestException() {
        ConcurrentRequestException ex = new ConcurrentRequestException("error.concurrent", new Object[] {},
                "idemp-key");
        when(messageSource.getMessage(eq(ex.getMessageKey()), any(), any(Locale.class)))
                .thenReturn("Concurrent request");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleConcurrentRequestException(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Concurrent request", response.getBody().message());
    }

    @Test
    void shouldReturn405WhenHttpRequestMethodNotSupported() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("PATCH",
                List.of("GET", "POST"));

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleMethodNotSupportedException(ex);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().message().contains("Request method 'PATCH' is not supported"));
    }

    @Test
    void shouldHandleGeneralException() {
        Exception ex = new Exception("General error");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleGeneralException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Sistemsel bir hata oluştu. Lütfen teknik destek ile iletişime geçin.",
                response.getBody().message());
    }

    @Test
    void shouldHandleHttpMediaTypeNotSupportedException() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(
                MediaType.APPLICATION_XML, List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler
                .handleMediaTypeNotSupportedException(ex);

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().message().contains("Desteklenmeyen medya türü"));
    }

    @Test
    void shouldHandleHttpRequestMethodNotSupportedWithNullSupportedMethods() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("DELETE",
                Collections.emptyList());

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleMethodNotSupportedException(ex);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}
