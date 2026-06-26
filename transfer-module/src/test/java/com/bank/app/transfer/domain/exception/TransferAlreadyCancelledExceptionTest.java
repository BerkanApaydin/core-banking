package com.bank.app.transfer.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class TransferAlreadyCancelledExceptionTest {

    @Test
    void shouldCreateWithId() {
        TransferAlreadyCancelledException ex = new TransferAlreadyCancelledException(1L);
        assertEquals("error.transfer_already_cancelled", ex.getMessageKey());
        assertArrayEquals(new Object[]{1L}, ex.getArgs());
        assertTrue(ex.getMessage().contains("1"));
    }

    @Test
    void shouldBeBusinessException() {
        TransferAlreadyCancelledException ex = new TransferAlreadyCancelledException(1L);
        assertInstanceOf(BusinessException.class, ex);
    }

    @Test
    void shouldReturnCorrectErrorCode() {
        TransferAlreadyCancelledException ex = new TransferAlreadyCancelledException(1L);
        assertEquals("TRANSFER_ALREADY_CANCELLED", ex.getErrorCode());
    }
}
