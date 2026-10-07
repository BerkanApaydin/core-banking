package com.bank.app.user.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.BusinessFailureKind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AuthenticationFailedExceptionTest {

    @Test
    void shouldCreateWithFixedMessageKeyAndEmptyArgs() {
        AuthenticationFailedException ex = new AuthenticationFailedException();
        assertEquals("error.authentication_failed", ex.getMessageKey());
        assertArrayEquals(new Object[]{}, ex.getArgs());
        assertEquals("Authentication failed.", ex.getMessage());
    }

    @Test
    void shouldCreateWithStaticDetailWithoutExposingArgs() {
        AuthenticationFailedException ex = new AuthenticationFailedException("Refresh token has expired.");
        assertEquals("error.authentication_failed", ex.getMessageKey());
        // D11/K13: static detail lives only in the default message for logs;
        // args stay empty so no template can ever interpolate it outward.
        assertArrayEquals(new Object[]{}, ex.getArgs());
        assertTrue(ex.getMessage().contains("Refresh token has expired."));
    }

    @Test
    void shouldCreateWithCauseOnlyAndNeverEchoFrameworkMessage() {
        // Simulates Spring's UsernameNotFoundException("User not found: admin"):
        // the username must not appear anywhere client-visible.
        Throwable cause = new RuntimeException("User not found: admin");
        AuthenticationFailedException ex = new AuthenticationFailedException(cause);
        assertEquals("error.authentication_failed", ex.getMessageKey());
        assertArrayEquals(new Object[]{}, ex.getArgs());
        assertSame(cause, ex.getCause());
        assertFalse(ex.getMessage().contains("admin"));
        assertFalse(ex.getMessage().contains("User not found"));
    }

    @Test
    void shouldBeBusinessException() {
        AuthenticationFailedException ex = new AuthenticationFailedException("test");
        assertInstanceOf(BusinessException.class, ex);
    }

    @Test
    void shouldReturnCorrectErrorCode() {
        AuthenticationFailedException ex = new AuthenticationFailedException("test");
        assertEquals("AUTHENTICATION_FAILED", ex.getErrorCode());
        assertEquals(BusinessFailureKind.AUTHENTICATION_FAILED, ex.getFailureKind());
    }
}
