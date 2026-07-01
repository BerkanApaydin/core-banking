package com.bank.app.user.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class TooManyFailedLoginAttemptsExceptionTest {

    @Test
    void shouldCreateWithMessage() {
        TooManyFailedLoginAttemptsException ex = new TooManyFailedLoginAttemptsException("5 attempts");
        assertEquals("Too many failed login attempts: 5 attempts", ex.getMessage());
        assertEquals("error.too_many_failed_login_attempts", ex.getMessageKey());
        assertArrayEquals(new Object[]{"5 attempts"}, ex.getArgs());
    }

    @Test
    void shouldBeBusinessException() {
        TooManyFailedLoginAttemptsException ex = new TooManyFailedLoginAttemptsException("test");
        assertInstanceOf(BusinessException.class, ex);
    }

    @Test
    void shouldReturnCorrectErrorCode() {
        TooManyFailedLoginAttemptsException ex = new TooManyFailedLoginAttemptsException("test");
        assertEquals("TOO_MANY_FAILED_LOGIN_ATTEMPTS", ex.getErrorCode());
    }
}
