package com.bank.app.infrastructure.adapter.in.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Locale;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import com.bank.app.common.domain.exception.AuthorizationException;
import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.BusinessFailureKind;
import com.bank.app.common.domain.exception.ConcurrentRequestException;
import com.bank.app.user.domain.exception.TooManyFailedLoginAttemptsException;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"null", "unchecked"})
class BusinessProblemHandlerTest {

    private BusinessProblemHandler handler;

    @Mock
    private MessageSource messageSource;

    @BeforeEach
    void setUp() {
        handler = new BusinessProblemHandler(new ProblemMessageResolver(messageSource), null);
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
        when(ex.getMessage()).thenReturn("Account not found. IBAN: TR1");
        when(ex.getErrorCode()).thenReturn("ACCOUNT_NOT_FOUND");
        when(messageSource.getMessage(eq(ex.getMessageKey()), any(), any(Locale.class)))
                .thenThrow(new NoSuchMessageException("No key"));

        ResponseEntity<ProblemDetail> response = handler.handleBusinessException(ex, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ACCOUNT_NOT_FOUND", response.getBody().getProperties().get("code"));
        assertEquals("Account not found. IBAN: TR1", response.getBody().getProperties().get("message"));
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
    void shouldHandleTooManyFailedLoginAttemptsException() {
        BusinessException ex = new TooManyFailedLoginAttemptsException("Too many failed login attempts");
        when(messageSource.getMessage(eq(ex.getMessageKey()), any(), any(Locale.class)))
                .thenReturn("Too many failed login attempts");

        ResponseEntity<ProblemDetail> response = handler.handleBusinessException(ex, null);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("TOO_MANY_FAILED_LOGIN_ATTEMPTS", response.getBody().getProperties().get("code"));
        assertEquals("Too many failed login attempts", response.getBody().getProperties().get("message"));
    }

    @SuppressWarnings("serial")
    private static final class TestRateLimitException extends BusinessException {
        TestRateLimitException() { super("error.rate_limit", new Object[]{}, "Rate limit"); }
        @Override public BusinessFailureKind getFailureKind() { return BusinessFailureKind.RATE_LIMITED; }
    }

    @SuppressWarnings("serial")
    private static final class TestDuplicateException extends BusinessException {
        TestDuplicateException() { super("error.duplicate", new Object[]{}, "Duplicate"); }
        @Override public BusinessFailureKind getFailureKind() { return BusinessFailureKind.CONFLICT; }
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
    void shouldHandleAuthorizationException() {
        AuthorizationException ex = new AuthorizationException("Authorization error");

        ResponseEntity<ProblemDetail> response = handler.handleAuthorizationException(ex, null);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ACCESS_DENIED", response.getBody().getProperties().get("code"));
        assertEquals("Authorization error", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldHandleAuthorizationExceptionWithResolvedMessage() {
        AuthorizationException ex = mock(AuthorizationException.class);
        when(ex.getMessageKey()).thenReturn("error.authorization");
        when(ex.getArgs()).thenReturn(new Object[]{});
        when(messageSource.getMessage(eq("error.authorization"), any(), any(Locale.class)))
                .thenReturn("Authorization failed");

        ResponseEntity<ProblemDetail> response = handler.handleAuthorizationException(ex, null);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Authorization failed", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldHandleAuthorizationExceptionWithFallbackToDefaultMessage() {
        AuthorizationException ex = mock(AuthorizationException.class);
        when(ex.getMessageKey()).thenReturn("error.authorization");
        when(ex.getArgs()).thenReturn(new Object[]{});
        when(ex.getMessage()).thenReturn("Transaction rejected");
        when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenThrow(new NoSuchMessageException("No key"));

        ResponseEntity<ProblemDetail> response = handler.handleAuthorizationException(ex, null);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Transaction rejected", response.getBody().getProperties().get("message"));
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
    void shouldIncrementOptimisticLockConflictCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BusinessProblemHandler metered =
                new BusinessProblemHandler(new ProblemMessageResolver(messageSource), registry);
        when(messageSource.getMessage(eq("error.optimistic_lock_conflict"), isNull(), any(Locale.class)))
                .thenReturn("Optimistic lock conflict");

        metered.handleOptimisticLockingFailureException(
                new OptimisticLockingFailureException("Conflict"), null);
        metered.handleOptimisticLockingFailureException(
                new OptimisticLockingFailureException("Conflict"), null);

        assertEquals(2.0, registry.counter("bank.optimistic.lock.conflicts",
                "exception", "OptimisticLockingFailureException").count());
    }

    @Test
    void shouldHandleOptimisticLockWithoutRegistry() {
        when(messageSource.getMessage(eq("error.optimistic_lock_conflict"), isNull(), any(Locale.class)))
                .thenReturn("Optimistic lock conflict");

        ResponseEntity<ProblemDetail> response = handler.handleOptimisticLockingFailureException(
                new OptimisticLockingFailureException("Conflict"), null);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
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
                .thenThrow(new NoSuchMessageException("No key"));

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
                .thenThrow(new NoSuchMessageException("No key"));

        ResponseEntity<ProblemDetail> response = handler.handleConcurrentRequestException(ex, null);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("CONCURRENT", response.getBody().getProperties().get("code"));
        assertEquals("", response.getBody().getProperties().get("message"));
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
