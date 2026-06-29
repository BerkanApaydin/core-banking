package com.bank.app.account.domain.exception;

import com.bank.app.account.application.exception.AccountNotFoundException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AccountExceptionTest {

    @Test
    void shouldCreateInvalidCurrencyException() {
        InvalidCurrencyException ex = new InvalidCurrencyException("XYZ");
        assertEquals("Invalid currency: XYZ", ex.getMessage());
        assertEquals("error.invalid_currency", ex.getMessageKey());
        assertArrayEquals(new Object[]{"XYZ"}, ex.getArgs());
    }

    @Test
    void shouldCreateInsufficientBalanceExceptionWithMessage() {
        InsufficientBalanceException ex = new InsufficientBalanceException("insufficient");
        assertEquals("insufficient", ex.getMessage());
    }

    @Test
    void shouldCreateInsufficientBalanceExceptionWithKeyAndArgs() {
        InsufficientBalanceException ex = new InsufficientBalanceException("key", new Object[]{"arg"}, "default");
        assertEquals("default", ex.getMessage());
        assertEquals("key", ex.getMessageKey());
        assertArrayEquals(new Object[]{"arg"}, ex.getArgs());
    }

    @Test
    void shouldCreateAccountNotFoundExceptionWithIban() {
        AccountNotFoundException ex = new AccountNotFoundException("TR290006200000000000000111");
        assertEquals("error.account_not_found", ex.getMessageKey());
        assertArrayEquals(new Object[]{"TR290006200000000000000111"}, ex.getArgs());
        assertEquals("Account not found. IBAN: TR290006200000000000000111", ex.getMessage());
        assertEquals("ACCOUNT_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void shouldCreateAccountNotFoundExceptionWithId() {
        AccountNotFoundException ex = new AccountNotFoundException(42L);
        assertEquals("error.account_not_found", ex.getMessageKey());
        assertArrayEquals(new Object[]{42L}, ex.getArgs());
        assertEquals("Account not found. ID: 42", ex.getMessage());
        assertEquals("ACCOUNT_NOT_FOUND", ex.getErrorCode());
    }
}
