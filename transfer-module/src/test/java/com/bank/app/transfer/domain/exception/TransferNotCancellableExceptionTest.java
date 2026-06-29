package com.bank.app.transfer.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class TransferNotCancellableExceptionTest {

    @Test
    void shouldCreateWithMessageKeyAndArgs() {
        TransferNotCancellableException ex = new TransferNotCancellableException("error.transfer_not_cancellable", new Object[]{1L}, "Transfer cannot be cancelled: 1");
        assertEquals("error.transfer_not_cancellable", ex.getMessageKey());
        assertArrayEquals(new Object[]{1L}, ex.getArgs());
        assertEquals("Transfer cannot be cancelled: 1", ex.getMessage());
    }

    @Test
    void shouldBeBusinessException() {
        TransferNotCancellableException ex = new TransferNotCancellableException("error.transfer_not_cancellable", new Object[]{1L}, "test");
        assertInstanceOf(BusinessException.class, ex);
    }

    @Test
    void shouldReturnCorrectErrorCode() {
        TransferNotCancellableException ex = new TransferNotCancellableException("error.transfer_not_cancellable", new Object[]{1L}, "test");
        assertEquals("TRANSFER_NOT_CANCELLABLE", ex.getErrorCode());
    }
}
