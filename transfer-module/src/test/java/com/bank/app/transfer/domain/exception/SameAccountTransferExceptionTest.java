package com.bank.app.transfer.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class SameAccountTransferExceptionTest {

    @Test
    void shouldCreateWithIban() {
        SameAccountTransferException ex = new SameAccountTransferException("TR290006200000000000000111");
        assertEquals("error.same_account_transfer", ex.getMessageKey());
        assertArrayEquals(new Object[]{"TR290006200000000000000111"}, ex.getArgs());
        assertTrue(ex.getMessage().contains("TR290006200000000000000111"));
    }

    @Test
    void shouldBeBusinessException() {
        SameAccountTransferException ex = new SameAccountTransferException("TR290006200000000000000111");
        assertInstanceOf(BusinessException.class, ex);
    }

    @Test
    void shouldReturnCorrectErrorCode() {
        SameAccountTransferException ex = new SameAccountTransferException("TR290006200000000000000111");
        assertEquals("SAME_ACCOUNT_TRANSFER", ex.getErrorCode());
    }
}
