package com.bank.app.transfer.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransferExceptionTest {

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
}
