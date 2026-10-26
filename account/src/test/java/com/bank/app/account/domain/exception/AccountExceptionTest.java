package com.bank.app.account.domain.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AccountExceptionTest {

    @Test
    void shouldCreateInvalidCurrencyException() {
        InvalidCurrencyException ex = new InvalidCurrencyException("XYZ");
        assertEquals("Invalid currency: XYZ", ex.getMessage());
        assertEquals("error.invalid_currency", ex.getMessageKey());
        assertArrayEquals(new Object[] { "XYZ" }, ex.getArgs());
    }

    @Test
    void shouldCreateInsufficientBalanceExceptionWithMessage() {
        InsufficientBalanceException ex = new InsufficientBalanceException("insufficient");
        assertEquals("insufficient", ex.getMessage());
    }

    @Test
    void shouldCreateInsufficientBalanceExceptionWithKeyAndArgs() {
        InsufficientBalanceException ex = new InsufficientBalanceException("key", new Object[] { "arg" }, "default");
        assertEquals("default", ex.getMessage());
        assertEquals("key", ex.getMessageKey());
        assertArrayEquals(new Object[] { "arg" }, ex.getArgs());
    }

}
