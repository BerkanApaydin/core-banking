package com.bank.app.common.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MessageSource messageSource;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        messageSource = mock(MessageSource.class);
        ReflectionTestUtils.setField(handler, "messageSource", messageSource);
    }

    @Test
    void testHandleNotFoundExceptions() {
        BusinessException ex = new AccountNotFoundException("TR1");
        when(messageSource.getMessage(eq(ex.getMessageKey()), any(), any(Locale.class)))
                .thenReturn("Account not found TR1");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleNotFoundExceptions(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Account not found TR1", response.getBody().message());
    }

    @Test
    void testHandleNotFoundExceptionsFallback() {
        BusinessException ex = new AccountNotFoundException("TR1");
        when(messageSource.getMessage(eq(ex.getMessageKey()), any(), any(Locale.class)))
                .thenThrow(new NoSuchMessageException("No key"));

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleNotFoundExceptions(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ex.getMessage(), response.getBody().message());
    }

    @Test
    void testHandleBusinessException() {
        BusinessException ex = new InsufficientBalanceException("error.insufficient_balance",
                new Object[] { "TR1", java.math.BigDecimal.TEN }, "Insufficient balance");
        when(messageSource.getMessage(eq(ex.getMessageKey()), any(), any(Locale.class)))
                .thenReturn("Insufficient balance");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleBusinessException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Insufficient balance", response.getBody().message());
    }

    @Test
    void testHandleValidationExceptions() {
        BindingResult bindingResult = new MapBindingResult(new HashMap<>(), "request");
        bindingResult.addError(new FieldError("request", "amount", "Amount must be positive"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<Map<String, String>> response = handler.handleValidationExceptions(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Amount must be positive", response.getBody().get("amount"));
    }

    @Test
    void testHandleIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleIllegalArgumentException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid argument", response.getBody().message());
    }

    @Test
    void testHandleAuthenticationException() {
        AuthenticationException ex = new AuthenticationException("Bad credentials") {
        };

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleAuthenticationException(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Bad credentials", response.getBody().message());
    }

    @Test
    void testHandleAccessDeniedException() {
        AccessDeniedException ex = new AccessDeniedException("Access denied");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleAccessDeniedException(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Access denied", response.getBody().message());
    }

    @Test
    void testHandleOptimisticLockingFailureException() {
        OptimisticLockingFailureException ex = new OptimisticLockingFailureException("Conflict");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler
                .handleOptimisticLockingFailureException(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().message().contains("eşzamanlı"));
    }

    @Test
    void testHandleHttpMessageNotReadableException() {
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
    void testHandleHttpMessageNotReadableExceptionWithEnumCause() {
        InvalidFormatException cause = new InvalidFormatException(null, "Invalid value", "VALUE3", DummyEnum.class);
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Not readable", cause, null);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler
                .handleHttpMessageNotReadableException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().message().contains("Geçersiz değer: VALUE3"));
    }

    @Test
    void testHandleHttpMessageNotReadableExceptionWithNonEnumCause() {
        InvalidFormatException cause = new InvalidFormatException(null, "Invalid value", "123", Integer.class);
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Not readable", cause, null);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler
                .handleHttpMessageNotReadableException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("İstek formatı geçersiz.", response.getBody().message());
    }

    @Test
    void testHandleHttpMessageNotReadableExceptionWithNullTargetTypeCause() {
        InvalidFormatException cause = new InvalidFormatException(null, "Invalid value", "123", null);
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Not readable", cause, null);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler
                .handleHttpMessageNotReadableException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("İstek formatı geçersiz.", response.getBody().message());
    }

    @Test
    void testTranslateWithNullMessage() {
        BusinessException ex = mock(BusinessException.class);
        when(ex.getMessageKey()).thenReturn(null);
        when(ex.getMessage()).thenReturn(null);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleBusinessException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("", response.getBody().message());
    }

    @Test
    void testHandleDataIntegrityViolationException() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("Violation");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler
                .handleDataIntegrityViolationException(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().message().contains("bütünlük"));
    }

    @Test
    void testHandleDataIntegrityViolationExceptionWithConstraintCause() {
        ConstraintViolationException cause = new ConstraintViolationException("Constraint fail", null, "uk_name");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("Violation", cause);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler
                .handleDataIntegrityViolationException(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().message().contains("Bu kayıt zaten mevcut"));
    }

    @Test
    void testHandleConcurrentRequestException() {
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
    void testHandleGeneralException() {
        Exception ex = new Exception("General error");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleGeneralException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().message().contains("Sistemsel"));
    }
}
