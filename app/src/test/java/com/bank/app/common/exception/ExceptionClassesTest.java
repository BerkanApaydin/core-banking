package com.bank.app.common.exception;

import com.bank.app.transfer.exception.TransferNotCancellableException;
import com.bank.app.account.exception.AccountNotFoundException;
import com.bank.app.account.exception.InvalidCurrencyException;
import com.bank.app.account.exception.InsufficientBalanceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;

class ExceptionClassesTest {

    @Test
    void shouldCreateTransferNotCancellableExceptionWithMessage() {
        TransferNotCancellableException ex = new TransferNotCancellableException("message");
        assertEquals("message", ex.getMessage());
        assertNull(ex.getMessageKey());
        assertNull(ex.getArgs());
    }

    @Test
    void shouldCreateTransferNotCancellableExceptionWithKeyAndArgs() {
        TransferNotCancellableException ex = new TransferNotCancellableException("key", new Object[]{"arg"}, "default");
        assertEquals("default", ex.getMessage());
        assertEquals("key", ex.getMessageKey());
        assertArrayEquals(new Object[]{"arg"}, ex.getArgs());
    }

    @Test
    void shouldCreateInvalidCurrencyException() {
        InvalidCurrencyException ex = new InvalidCurrencyException("XYZ");
        assertEquals("XYZ", ex.getMessage());
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
    void shouldCreateConcurrentRequestExceptionWithMessage() {
        ConcurrentRequestException ex = new ConcurrentRequestException("concurrent");
        assertEquals("concurrent", ex.getMessage());
    }

    @Test
    void shouldCreateConcurrentRequestExceptionWithKeyAndArgs() {
        ConcurrentRequestException ex = new ConcurrentRequestException("key", new Object[]{"arg"}, "default");
        assertEquals("default", ex.getMessage());
        assertEquals("key", ex.getMessageKey());
        assertArrayEquals(new Object[]{"arg"}, ex.getArgs());
    }

    @Test
    void shouldCreateAccountNotFoundExceptionWithIban() {
        AccountNotFoundException ex = new AccountNotFoundException("TR290006200000000000000111");
        assertEquals("error.account_not_found_iban", ex.getMessageKey());
        assertArrayEquals(new Object[]{"TR290006200000000000000111"}, ex.getArgs());
        assertEquals("Hesap bulunamad\u0131. IBAN: TR290006200000000000000111", ex.getMessage());
        assertEquals("ACCOUNT_NOT_FOUND", ex.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    @Test
    void shouldCreateAccountNotFoundExceptionWithId() {
        AccountNotFoundException ex = new AccountNotFoundException(42L);
        assertEquals("error.account_not_found_id", ex.getMessageKey());
        assertArrayEquals(new Object[]{42L}, ex.getArgs());
        assertEquals("Hesap bulunamad\u0131. ID: 42", ex.getMessage());
        assertEquals("ACCOUNT_NOT_FOUND", ex.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }
}
