package com.bank.app.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyMismatchExceptionTest {

    @Test
    void shouldCreateWithMessage() {
        CurrencyMismatchException ex = new CurrencyMismatchException("TRY ile USD toplanamaz");
        assertEquals("TRY ile USD toplanamaz", ex.getMessage());
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
