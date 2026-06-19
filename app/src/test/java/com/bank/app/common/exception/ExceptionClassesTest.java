package com.bank.app.common.exception;

import com.bank.app.transfer.exception.TransferNotCancellableException;
import com.bank.app.account.exception.InvalidCurrencyException;
import com.bank.app.account.exception.InsufficientBalanceException;
import org.junit.jupiter.api.Test;
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
}
