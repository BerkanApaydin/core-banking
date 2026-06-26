package com.bank.app.transfer.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.transfer.domain.TransferStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class TransferNotPendingExceptionTest {

    @Test
    void shouldCreateWithStatus() {
        TransferNotPendingException ex = new TransferNotPendingException(TransferStatus.COMPLETED);
        assertEquals("error.transfer_not_pending", ex.getMessageKey());
        assertArrayEquals(new Object[]{TransferStatus.COMPLETED}, ex.getArgs());
        assertTrue(ex.getMessage().contains("COMPLETED"));
    }

    @Test
    void shouldBeBusinessException() {
        TransferNotPendingException ex = new TransferNotPendingException(TransferStatus.CANCELLED);
        assertInstanceOf(BusinessException.class, ex);
    }

    @Test
    void shouldReturnCorrectErrorCode() {
        TransferNotPendingException ex = new TransferNotPendingException(TransferStatus.FAILED);
        assertEquals("TRANSFER_NOT_PENDING", ex.getErrorCode());
    }
}
