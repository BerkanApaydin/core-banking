package com.bank.app.account.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AccountClosedExceptionTest {

    @Test
    void shouldCreateWithIban() {
        AccountClosedException ex = new AccountClosedException("TR290006200000000000000111");
        assertEquals("Hesap kapatılmış: TR290006200000000000000111", ex.getMessage());
        assertEquals("error.account_closed", ex.getMessageKey());
        assertArrayEquals(new Object[]{"TR290006200000000000000111"}, ex.getArgs());
    }

    @Test
    void shouldBeBusinessException() {
        AccountClosedException ex = new AccountClosedException("TR111111111111111111111111");
        assertInstanceOf(BusinessException.class, ex);
    }

    @Test
    void shouldReturnCorrectErrorCode() {
        AccountClosedException ex = new AccountClosedException("TR111111111111111111111111");
        assertEquals("ACCOUNT_CLOSED", ex.getErrorCode());
    }
}
