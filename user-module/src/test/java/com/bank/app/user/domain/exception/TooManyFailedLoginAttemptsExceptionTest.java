package com.bank.app.user.domain.exception;

import com.bank.app.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TooManyFailedLoginAttemptsExceptionTest {

    @Test
    void shouldCreateWithMessage() {
        TooManyFailedLoginAttemptsException ex = new TooManyFailedLoginAttemptsException("5 deneme");
        assertEquals("Çok fazla başarısız giriş denemesi: 5 deneme", ex.getMessage());
        assertEquals("error.too_many_failed_login_attempts", ex.getMessageKey());
        assertArrayEquals(new Object[]{"5 deneme"}, ex.getArgs());
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
