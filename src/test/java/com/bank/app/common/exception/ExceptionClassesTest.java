package com.bank.app.common.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExceptionClassesTest {

    @Test
    void testExceptions() {
        TransferNotCancellableException ex1 = new TransferNotCancellableException("message");
        assertEquals("message", ex1.getMessage());
        assertNull(ex1.getMessageKey());
        assertNull(ex1.getArgs());

        TransferNotCancellableException ex1WithArgs = new TransferNotCancellableException("key", new Object[]{"arg"}, "default");
        assertEquals("default", ex1WithArgs.getMessage());
        assertEquals("key", ex1WithArgs.getMessageKey());
        assertArrayEquals(new Object[]{"arg"}, ex1WithArgs.getArgs());

        InvalidCurrencyException ex2 = new InvalidCurrencyException("XYZ");
        assertEquals("XYZ", ex2.getMessage());

        InsufficientBalanceException ex3 = new InsufficientBalanceException("insufficient");
        assertEquals("insufficient", ex3.getMessage());

        InsufficientBalanceException ex3WithArgs = new InsufficientBalanceException("key", new Object[]{"arg"}, "default");
        assertEquals("default", ex3WithArgs.getMessage());
        assertEquals("key", ex3WithArgs.getMessageKey());
        assertArrayEquals(new Object[]{"arg"}, ex3WithArgs.getArgs());

        ConcurrentRequestException ex4 = new ConcurrentRequestException("concurrent");
        assertEquals("concurrent", ex4.getMessage());

        ConcurrentRequestException ex4WithArgs = new ConcurrentRequestException("key", new Object[]{"arg"}, "default");
        assertEquals("default", ex4WithArgs.getMessage());
        assertEquals("key", ex4WithArgs.getMessageKey());
        assertArrayEquals(new Object[]{"arg"}, ex4WithArgs.getArgs());
    }
}
