package com.bank.app.infrastructure.adapter.in.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import com.bank.app.user.application.port.out.AuthenticationBackendUnavailableException;
import com.bank.app.user.application.port.out.LoginAttemptStoreUnavailableException;
import com.bank.app.user.application.port.out.RevocationStoreUnavailableException;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"null", "unchecked"})
class SecurityProblemHandlerTest {

    private SecurityProblemHandler handler;

    @Mock
    private MessageSource messageSource;

    @BeforeEach
    void setUp() {
        handler = new SecurityProblemHandler(new ProblemMessageResolver(messageSource));
    }

    @Test
    void shouldHandleAuthenticationException() {
        AuthenticationException ex = new AuthenticationException("Bad credentials") {};
        when(messageSource.getMessage(eq("error.authentication_failed"), any(), any(Locale.class)))
                .thenReturn("Authentication failed.");

        ResponseEntity<ProblemDetail> response = handler.handleAuthenticationException(ex, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("AUTHENTICATION_FAILED", response.getBody().getProperties().get("code"));
        assertEquals("Authentication failed.", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldSanitizeAuthenticationFailureWhenBundleIsMissing() {
        // Real backend detail must never reach the client, even without i18n.
        AuthenticationException ex = new AuthenticationException("Bad credentials for john: password expired") {};
        when(messageSource.getMessage(eq("error.authentication_failed"), any(), any(Locale.class)))
                .thenReturn("error.authentication_failed");

        ResponseEntity<ProblemDetail> response = handler.handleAuthenticationException(ex, null);

        assertNotNull(response.getBody());
        assertEquals("Authentication failed.", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldSanitizeAuthenticationFailureWhenBundleReturnsNull() {
        AuthenticationException ex = new AuthenticationException("Bad credentials") {};
        when(messageSource.getMessage(eq("error.authentication_failed"), any(), any(Locale.class)))
                .thenReturn(null);

        ResponseEntity<ProblemDetail> response = handler.handleAuthenticationException(ex, null);

        assertNotNull(response.getBody());
        assertEquals("Authentication failed.", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldHandleAccessDeniedException() {
        AccessDeniedException ex = new AccessDeniedException("Access denied");
        when(messageSource.getMessage(eq("error.access_denied"), any(), any(Locale.class)))
                .thenReturn("Access denied.");

        ResponseEntity<ProblemDetail> response = handler.handleAccessDeniedException(ex, null);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ACCESS_DENIED", response.getBody().getProperties().get("code"));
        assertEquals("Access denied.", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldSanitizeAccessDeniedWhenBundleIsMissing() {
        AccessDeniedException ex = new AccessDeniedException("missing role ROLE_ADMIN on /api/v1/users/42");
        when(messageSource.getMessage(eq("error.access_denied"), any(), any(Locale.class)))
                .thenReturn("error.access_denied");

        ResponseEntity<ProblemDetail> response = handler.handleAccessDeniedException(ex, null);

        assertNotNull(response.getBody());
        assertEquals("Access denied.", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldSanitizeAccessDeniedWhenBundleReturnsNull() {
        AccessDeniedException ex = new AccessDeniedException("Access denied");
        when(messageSource.getMessage(eq("error.access_denied"), any(), any(Locale.class)))
                .thenReturn(null);

        ResponseEntity<ProblemDetail> response = handler.handleAccessDeniedException(ex, null);

        assertNotNull(response.getBody());
        assertEquals("Access denied.", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldReturnServiceUnavailableForAuthenticationBackendFailure() {
        when(messageSource.getMessage(eq("error.security_backend_unavailable"), isNull(), any(Locale.class)))
                .thenReturn("Security service temporarily unavailable.");
        ResponseEntity<ProblemDetail> response = handler.handleAuthenticationBackendUnavailable(
                new AuthenticationBackendUnavailableException(new RuntimeException("private database detail")), null);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("SECURITY_BACKEND_UNAVAILABLE", response.getBody().getProperties().get("code"));
        assertFalse(response.getBody().getDetail().contains("private database detail"));
    }

    @Test
    void shouldReturnServiceUnavailableForLoginAttemptBackendFailure() {
        when(messageSource.getMessage(eq("error.security_backend_unavailable"), isNull(), any(Locale.class)))
                .thenReturn("Security service temporarily unavailable.");

        ResponseEntity<ProblemDetail> response = handler.handleLoginAttemptStoreUnavailable(
                new LoginAttemptStoreUnavailableException(new RuntimeException("Redis password leaked here")), null);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("SECURITY_BACKEND_UNAVAILABLE", response.getBody().getProperties().get("code"));
        assertEquals("Security service temporarily unavailable.", response.getBody().getDetail());
    }

    @Test
    void shouldReturnServiceUnavailableForRevocationWriteFailure() {
        when(messageSource.getMessage(eq("error.security_backend_unavailable"), isNull(), any(Locale.class)))
                .thenReturn("Security service temporarily unavailable.");

        ResponseEntity<ProblemDetail> response = handler.handleRevocationStoreUnavailable(
                new RevocationStoreUnavailableException(new RuntimeException("secret Redis detail")), null);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("SECURITY_BACKEND_UNAVAILABLE", response.getBody().getProperties().get("code"));
        assertEquals("Security service temporarily unavailable.", response.getBody().getDetail());
        assertFalse(response.getBody().getDetail().contains("secret Redis detail"));
    }
}
