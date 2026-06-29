package com.bank.app.transfer.application.exception;

import com.bank.app.common.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class TransferNotFoundExceptionTest {

    @Test
    void shouldCreateWithId() {
        TransferNotFoundException ex = new TransferNotFoundException(42L);
        assertEquals("Transfer not found. ID: 42", ex.getMessage());
        assertEquals("error.transfer_not_found", ex.getMessageKey());
        assertArrayEquals(new Object[]{42L}, ex.getArgs());
    }

    @Test
    void shouldBeBusinessException() {
        TransferNotFoundException ex = new TransferNotFoundException(1L);
        assertInstanceOf(BusinessException.class, ex);
    }

    @Test
    void shouldReturnCorrectErrorCode() {
        TransferNotFoundException ex = new TransferNotFoundException(1L);
        assertEquals("TRANSFER_NOT_FOUND", ex.getErrorCode());
    }
}
