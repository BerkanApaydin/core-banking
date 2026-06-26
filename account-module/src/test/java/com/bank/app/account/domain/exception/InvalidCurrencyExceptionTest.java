package com.bank.app.account.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class InvalidCurrencyExceptionTest {

    @Test
    void shouldCreateWithCurrency() {
        InvalidCurrencyException ex = new InvalidCurrencyException("XYZ");
        assertEquals("Geçersiz para birimi: XYZ", ex.getMessage());
        assertEquals("error.invalid_currency", ex.getMessageKey());
        assertArrayEquals(new Object[]{"XYZ"}, ex.getArgs());
    }

    @Test
    void shouldBeBusinessException() {
        InvalidCurrencyException ex = new InvalidCurrencyException("ABC");
        assertInstanceOf(BusinessException.class, ex);
    }

    @Test
    void shouldReturnCorrectErrorCode() {
        InvalidCurrencyException ex = new InvalidCurrencyException("ABC");
        assertEquals("INVALID_CURRENCY", ex.getErrorCode());
    }

    @Test
    void shouldCreateWithExplicitMessageKeyAndArgs() {
        InvalidCurrencyException ex = new InvalidCurrencyException("custom.key", new Object[]{"USD"}, "Default message");
        assertEquals("custom.key", ex.getMessageKey());
        assertArrayEquals(new Object[]{"USD"}, ex.getArgs());
        assertEquals("Default message", ex.getMessage());
    }
}
