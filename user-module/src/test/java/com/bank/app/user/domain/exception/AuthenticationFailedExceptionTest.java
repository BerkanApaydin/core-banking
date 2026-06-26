package com.bank.app.user.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AuthenticationFailedExceptionTest {

    @Test
    void shouldCreateWithMessage() {
        AuthenticationFailedException ex = new AuthenticationFailedException("yanlış şifre");
        assertEquals("error.authentication_failed", ex.getMessageKey());
        assertArrayEquals(new Object[]{"yanlış şifre"}, ex.getArgs());
        assertTrue(ex.getMessage().contains("yanlış şifre"));
    }

    @Test
    void shouldCreateWithMessageAndCause() {
        Throwable cause = new RuntimeException("root cause");
        AuthenticationFailedException ex = new AuthenticationFailedException("yanlış şifre", cause);
        assertEquals("error.authentication_failed", ex.getMessageKey());
        assertArrayEquals(new Object[]{"yanlış şifre"}, ex.getArgs());
        assertSame(cause, ex.getCause());
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
    }
}
