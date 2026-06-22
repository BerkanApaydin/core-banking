package com.bank.app.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BusinessExceptionTest {

    @Test
    void shouldReturnErrorCodeFromMessageKey() {
        BusinessException ex = new BusinessException("error.test_error", new Object[]{}, "Test") {};
        assertEquals("TEST_ERROR", ex.getErrorCode());
    }

    @Test
    void shouldReturnErrorCodeFromConcreteSubclass() {
        CurrencyMismatchException ex = new CurrencyMismatchException("test");
        assertEquals("CURRENCY_MISMATCH", ex.getErrorCode());
    }

    @Test
    void shouldReturnEmptyErrorCodeForAnonymousClass() {
        BusinessException ex = new BusinessException("Test message") {};
        assertEquals("", ex.getErrorCode());
    }

    @Test
    void shouldReturnMessageWhenProvided() {
        BusinessException ex = new BusinessException("Doğrudan mesaj") {};
        assertEquals("Doğrudan mesaj", ex.getMessage());
        assertNull(ex.getMessageKey());
        assertNull(ex.getArgs());
    }

    @Test
    void shouldReturnMessageFromKeyAndArgs() {
        BusinessException ex = new BusinessException("error.test", new Object[]{"arg1"}, "Default: arg1") {};
        assertEquals("Default: arg1", ex.getMessage());
        assertEquals("error.test", ex.getMessageKey());
        assertArrayEquals(new Object[]{"arg1"}, ex.getArgs());
    }
}
