package com.bank.app.common.adapter.in.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

import com.bank.app.common.domain.exception.AuthorizationException;
import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.ConcurrentRequestException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.hibernate.exception.ConstraintViolationException;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"null", "unchecked"})
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
        BusinessException ex = mock(BusinessException.class);
        when(ex.getMessageKey()).thenReturn("error.account_not_found_iban");
        when(ex.getArgs()).thenReturn(new Object[]{"TR1"});
        when(ex.getErrorCode()).thenReturn("ACCOUNT_NOT_FOUND");
        when(messageSource.getMessage(eq(ex.getMessageKey()), any(), any(Locale.class)))
                .thenReturn("Account not found TR1");

        ResponseEntity<ProblemDetail> response = handler.handleBusinessException(ex, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ACCOUNT_NOT_FOUND", response.getBody().getProperties().get("code"));
        assertEquals("Account not found TR1", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldHandleNotFoundExceptionsFallback() {
        BusinessException ex = mock(BusinessException.class);
        when(ex.getMessageKey()).thenReturn("error.account_not_found_iban");
        when(ex.getArgs()).thenReturn(new Object[]{"TR1"});
        when(ex.getMessage()).thenReturn("Hesap bulunamadı. IBAN: TR1");
        when(ex.getErrorCode()).thenReturn("ACCOUNT_NOT_FOUND");
        when(messageSource.getMessage(eq(ex.getMessageKey()), any(), any(Locale.class)))
                .thenThrow(new org.springframework.context.NoSuchMessageException("No key"));

        ResponseEntity<ProblemDetail> response = handler.handleBusinessException(ex, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ACCOUNT_NOT_FOUND", response.getBody().getProperties().get("code"));
        assertEquals("Hesap bulunamadı. IBAN: TR1", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldHandleBusinessException() {
        BusinessException ex = mock(BusinessException.class);
        when(ex.getMessageKey()).thenReturn("error.insufficient_balance");
        when(ex.getArgs()).thenReturn(new Object[]{"TR1", BigDecimal.TEN});
        when(ex.getErrorCode()).thenReturn("INSUFFICIENT_BALANCE");
        when(messageSource.getMessage(eq(ex.getMessageKey()), any(), any(Locale.class)))
                .thenReturn("Insufficient balance");

        ResponseEntity<ProblemDetail> response = handler.handleBusinessException(ex, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INSUFFICIENT_BALANCE", response.getBody().getProperties().get("code"));
        assertEquals("Insufficient balance", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldHandleValidationExceptions() {
        BindingResult bindingResult = new MapBindingResult(new HashMap<>(), "request");
        bindingResult.addError(new FieldError("request", "amount", "Amount must be positive"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ProblemDetail> response = handler.handleValidationExceptions(ex, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VALIDATION_FAILED", response.getBody().getProperties().get("code"));
        Map<String, String> errors = (Map<String, String>) response.getBody().getProperties().get("errors");
        assertNotNull(errors);
        assertEquals("Amount must be positive", errors.get("amount"));
    }

    @Test
    void shouldHandleIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument");

        ResponseEntity<ProblemDetail> response = handler.handleIllegalArgumentException(ex, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_ARGUMENT", response.getBody().getProperties().get("code"));
        assertEquals("Invalid argument", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldHandleAuthenticationException() {
        AuthenticationException ex = new AuthenticationException("Bad credentials") {};

        ResponseEntity<ProblemDetail> response = handler.handleAuthenticationException(ex, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("AUTHENTICATION_FAILED", response.getBody().getProperties().get("code"));
        assertEquals("Bad credentials", response.getBody().getProperties().get("message"));
    }

    private static final class TestRateLimitException extends BusinessException {
        TestRateLimitException() { super("error.rate_limit", new Object[]{}, "Rate limit"); }
        @Override public int getHttpStatusCode() { return 429; }
    }

    private static final class TestDuplicateException extends BusinessException {
        TestDuplicateException() { super("error.duplicate", new Object[]{}, "Duplicate"); }
        @Override public int getHttpStatusCode() { return 409; }
    }

    @Test
    void shouldHandleTooManyFailedLoginAttemptsException() {
        BusinessException ex = mock(BusinessException.class);
        when(ex.getMessageKey()).thenReturn("error.too_many_failed_login_attempts");
        when(ex.getArgs()).thenReturn(new Object[]{});
        when(ex.getErrorCode()).thenReturn("TOO_MANY_FAILED_LOGIN_ATTEMPTS");
        when(messageSource.getMessage(eq(ex.getMessageKey()), any(), any(Locale.class)))
                .thenReturn("Çok fazla başarısız giriş denemesi");

        ResponseEntity<ProblemDetail> response = handler.handleBusinessException(ex, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("TOO_MANY_FAILED_LOGIN_ATTEMPTS", response.getBody().getProperties().get("code"));
        assertEquals("Çok fazla başarısız giriş denemesi", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldResolveRateLimitBranchInResolveHttpStatus() {
        TestRateLimitException ex = new TestRateLimitException();
        when(messageSource.getMessage(eq("error.rate_limit"), any(), any(Locale.class)))
                .thenReturn("Rate limit exceeded");

        ResponseEntity<ProblemDetail> response = handler.handleBusinessException(ex, null);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
    }

    @Test
    void shouldResolveDuplicateBranchInResolveHttpStatus() {
        TestDuplicateException ex = new TestDuplicateException();
        when(messageSource.getMessage(eq("error.duplicate"), any(), any(Locale.class)))
                .thenReturn("Duplicate");

        ResponseEntity<ProblemDetail> response = handler.handleBusinessException(ex, null);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void shouldHandleAccessDeniedException() {
        AccessDeniedException ex = new AccessDeniedException("Access denied");

        ResponseEntity<ProblemDetail> response = handler.handleAccessDeniedException(ex, null);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ACCESS_DENIED", response.getBody().getProperties().get("code"));
        assertEquals("Access denied", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldHandleAuthorizationException() {
        AuthorizationException ex = new AuthorizationException("Yetki hatası");

        ResponseEntity<ProblemDetail> response = handler.handleAuthorizationException(ex, null);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ACCESS_DENIED", response.getBody().getProperties().get("code"));
        assertEquals("Yetki hatası", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldHandleAuthorizationExceptionWithResolvedMessage() {
        AuthorizationException ex = mock(AuthorizationException.class);
        when(ex.getMessageKey()).thenReturn("error.authorization");
        when(ex.getArgs()).thenReturn(new Object[]{});
        when(messageSource.getMessage(eq("error.authorization"), any(), any(Locale.class)))
                .thenReturn("Yetkilendirme başarısız");

        ResponseEntity<ProblemDetail> response = handler.handleAuthorizationException(ex, null);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Yetkilendirme başarısız", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldHandleAuthorizationExceptionWithFallbackToDefaultMessage() {
        AuthorizationException ex = mock(AuthorizationException.class);
        when(ex.getMessageKey()).thenReturn("error.authorization");
        when(ex.getArgs()).thenReturn(new Object[]{});
        when(ex.getMessage()).thenReturn("İşlem reddedildi");
        when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenThrow(new org.springframework.context.NoSuchMessageException("No key"));

        ResponseEntity<ProblemDetail> response = handler.handleAuthorizationException(ex, null);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("İşlem reddedildi", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldHandleAuthorizationExceptionWithNullMessage() {
        AuthorizationException ex = mock(AuthorizationException.class);
        when(ex.getMessageKey()).thenReturn(null);
        when(ex.getMessage()).thenReturn(null);

        ResponseEntity<ProblemDetail> response = handler.handleAuthorizationException(ex, null);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldHandleOptimisticLockingFailureException() {
        OptimisticLockingFailureException ex = new OptimisticLockingFailureException("Conflict");
        when(messageSource.getMessage(eq("error.optimistic_lock_conflict"), isNull(), any(Locale.class)))
                .thenReturn("Optimistic lock conflict");

        ResponseEntity<ProblemDetail> response = handler.handleOptimisticLockingFailureException(ex, null);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("OPTIMISTIC_LOCK_CONFLICT", response.getBody().getProperties().get("code"));
        assertEquals("Optimistic lock conflict", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldHandleHttpMessageNotReadableException() {
        HttpInputMessage httpInputMessage = mock(HttpInputMessage.class);
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Not readable", null, httpInputMessage);
        when(messageSource.getMessage(eq("error.invalid_format"), isNull(), any(Locale.class)))
                .thenReturn("Invalid format");

        ResponseEntity<ProblemDetail> response = handler.handleHttpMessageNotReadableException(ex, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_FORMAT", response.getBody().getProperties().get("code"));
        assertEquals("Invalid format", response.getBody().getProperties().get("message"));
    }

    enum DummyEnum { VALUE1, VALUE2 }

    @Test
    void shouldHandleHttpMessageNotReadableExceptionWithEnumCause() {
        InvalidFormatException cause = new InvalidFormatException(null, "Invalid value", "VALUE3", DummyEnum.class);
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Not readable", cause, null);
        when(messageSource.getMessage(eq("error.invalid_enum_value"), any(), any(Locale.class)))
                .thenReturn("Invalid value: VALUE3. Accepted values: [VALUE1, VALUE2]");
        when(messageSource.getMessage(eq("error.invalid_format"), isNull(), any(Locale.class)))
                .thenReturn("Invalid format");

        ResponseEntity<ProblemDetail> response = handler.handleHttpMessageNotReadableException(ex, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_ENUM_VALUE", response.getBody().getProperties().get("code"));
        assertTrue(((String) response.getBody().getProperties().get("message")).contains("VALUE3"));
    }

    @Test
    void shouldHandleHttpMessageNotReadableExceptionWithNonEnumCause() {
        InvalidFormatException cause = new InvalidFormatException(null, "Invalid value", "123", Integer.class);
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Not readable", cause, null);
        when(messageSource.getMessage(eq("error.invalid_format"), isNull(), any(Locale.class)))
                .thenReturn("Invalid format");

        ResponseEntity<ProblemDetail> response = handler.handleHttpMessageNotReadableException(ex, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_FORMAT", response.getBody().getProperties().get("code"));
        assertEquals("Invalid format", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldHandleHttpMessageNotReadableExceptionWithNullTargetTypeCause() {
        InvalidFormatException cause = new InvalidFormatException(null, "Invalid value", "123", null);
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Not readable", cause, null);
        when(messageSource.getMessage(eq("error.invalid_format"), isNull(), any(Locale.class)))
                .thenReturn("Invalid format");

        ResponseEntity<ProblemDetail> response = handler.handleHttpMessageNotReadableException(ex, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_FORMAT", response.getBody().getProperties().get("code"));
        assertEquals("Invalid format", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldTranslateWithNullMessage() {
        BusinessException ex = mock(BusinessException.class);
        when(ex.getMessageKey()).thenReturn(null);
        when(ex.getMessage()).thenReturn(null);
        when(ex.getErrorCode()).thenReturn("BUSINESS_ERROR");

        ResponseEntity<ProblemDetail> response = handler.handleBusinessException(ex, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BUSINESS_ERROR", response.getBody().getProperties().get("code"));
        assertEquals("", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldHandleDataIntegrityViolationException() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("Violation");
        when(messageSource.getMessage(eq("error.db_integrity_violation"), isNull(), any(Locale.class)))
                .thenReturn("DB integrity violation");

        ResponseEntity<ProblemDetail> response = handler.handleDataIntegrityViolationException(ex, null);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("DB_INTEGRITY_VIOLATION", response.getBody().getProperties().get("code"));
        assertEquals("DB integrity violation", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldHandleDataIntegrityViolationExceptionWithConstraintCause() {
        ConstraintViolationException cause = new ConstraintViolationException("Constraint fail", null, "uk_name");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("Violation", cause);
        when(messageSource.getMessage(eq("error.unique_constraint_violation"), isNull(), any(Locale.class)))
                .thenReturn("Unique constraint violation");

        ResponseEntity<ProblemDetail> response = handler.handleDataIntegrityViolationException(ex, null);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UNIQUE_CONSTRAINT_VIOLATION", response.getBody().getProperties().get("code"));
        assertEquals("Unique constraint violation", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldHandleConcurrentRequestException() {
        ConcurrentRequestException ex = new ConcurrentRequestException("error.concurrent", new Object[] {}, "idemp-key");
        when(messageSource.getMessage(eq(ex.getMessageKey()), any(), any(Locale.class)))
                .thenReturn("Concurrent request");

        ResponseEntity<ProblemDetail> response = handler.handleConcurrentRequestException(ex, null);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("CONCURRENT", response.getBody().getProperties().get("code"));
        assertEquals("Concurrent request", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldHandleConcurrentRequestExceptionWhenMessageIsEmpty() {
        ConcurrentRequestException ex = new ConcurrentRequestException("just a message");

        ResponseEntity<ProblemDetail> response = handler.handleConcurrentRequestException(ex, null);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("CONCURRENT_REQUEST", response.getBody().getProperties().get("code"));
        assertEquals("just a message", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldHandleConcurrentRequestExceptionWhenMessageSourceThrows() {
        ConcurrentRequestException ex = new ConcurrentRequestException("error.concurrent", new Object[] {}, "fallback message");
        when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenThrow(new org.springframework.context.NoSuchMessageException("No key"));

        ResponseEntity<ProblemDetail> response = handler.handleConcurrentRequestException(ex, null);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("CONCURRENT", response.getBody().getProperties().get("code"));
        assertEquals("fallback message", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldHandleConcurrentRequestExceptionWithNullDefaultMessage() {
        ConcurrentRequestException ex = new ConcurrentRequestException("error.concurrent", new Object[] {}, null);
        when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenThrow(new org.springframework.context.NoSuchMessageException("No key"));

        ResponseEntity<ProblemDetail> response = handler.handleConcurrentRequestException(ex, null);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("CONCURRENT", response.getBody().getProperties().get("code"));
        assertEquals("", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldReturn405WhenHttpRequestMethodNotSupported() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("PATCH", List.of("GET", "POST"));

        ResponseEntity<ProblemDetail> response = handler.handleMethodNotSupportedException(ex, null);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("METHOD_NOT_ALLOWED", response.getBody().getProperties().get("code"));
        assertTrue(((String) response.getBody().getProperties().get("message")).contains("Request method 'PATCH' is not supported"));
    }

    @Test
    void shouldHandleGeneralException() {
        Exception ex = new Exception("General error");

        ResponseEntity<ProblemDetail> response = handler.handleGeneralException(ex, null);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INTERNAL_ERROR", response.getBody().getProperties().get("code"));
    }

    @Test
    void shouldHandleHttpMediaTypeNotSupportedException() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(MediaType.APPLICATION_XML, List.of(MediaType.APPLICATION_JSON));
        when(messageSource.getMessage(eq("error.unsupported_media_type"), any(), any(Locale.class)))
                .thenReturn("Unsupported media type: application/xml");

        ResponseEntity<ProblemDetail> response = handler.handleMediaTypeNotSupportedException(ex, null);

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UNSUPPORTED_MEDIA_TYPE", response.getBody().getProperties().get("code"));
        assertTrue(((String) response.getBody().getProperties().get("message")).contains("Unsupported media type"));
    }

    @Test
    void shouldHandleHttpRequestMethodNotSupportedWithNullSupportedMethods() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("DELETE", Collections.emptyList());

        ResponseEntity<ProblemDetail> response = handler.handleMethodNotSupportedException(ex, null);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("METHOD_NOT_ALLOWED", response.getBody().getProperties().get("code"));
    }

    @Test
    void shouldSetInstanceFromRequestWhenRequestIsNonNull() {
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(false)).thenReturn("uri=/api/test");
        BusinessException ex = mock(BusinessException.class);
        when(ex.getMessageKey()).thenReturn("error.test");
        when(ex.getArgs()).thenReturn(new Object[]{});
        when(ex.getErrorCode()).thenReturn("TEST_ERROR");
        when(messageSource.getMessage(eq("error.test"), any(), any(Locale.class)))
                .thenReturn("Test error");

        ResponseEntity<ProblemDetail> response = handler.handleBusinessException(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(URI.create("/api/test"), response.getBody().getInstance());
    }

    @Test
    void shouldHandleRequestWithMalformedDescription() {
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(false)).thenThrow(new RuntimeException("Bad description"));
        BusinessException ex = mock(BusinessException.class);
        when(ex.getMessageKey()).thenReturn("error.test");
        when(ex.getArgs()).thenReturn(new Object[]{});
        when(ex.getErrorCode()).thenReturn("TEST_ERROR");
        when(messageSource.getMessage(eq("error.test"), any(), any(Locale.class)))
                .thenReturn("Test error");

        ResponseEntity<ProblemDetail> response = handler.handleBusinessException(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNull(response.getBody().getInstance());
    }
}
