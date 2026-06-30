package com.bank.app.common.domain.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class CurrencyMismatchExceptionTest {

    @Test
    void shouldCreateWithMessage() {
        CurrencyMismatchException ex = new CurrencyMismatchException("TRY and USD cannot be added");
        assertEquals("TRY and USD cannot be added", ex.getMessage());
        assertNull(ex.getMessageKey());
        assertNull(ex.getArgs());
    }

    @Test
    void shouldDeriveErrorCodeFromClassName() {
        CurrencyMismatchException ex = new CurrencyMismatchException("test");
        assertEquals("CURRENCY_MISMATCH", ex.getErrorCode());
    }

    @Test
    void shouldBeBusinessException() {
        CurrencyMismatchException ex = new CurrencyMismatchException("test");
        assertInstanceOf(BusinessException.class, ex);
    }
}
