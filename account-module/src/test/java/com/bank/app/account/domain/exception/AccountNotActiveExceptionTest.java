package com.bank.app.account.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AccountNotActiveExceptionTest {

    @Test
    void shouldCreateWithIban() {
        AccountNotActiveException ex = new AccountNotActiveException("TR290006200000000000000111");
        assertEquals("Hesap aktif değil: TR290006200000000000000111", ex.getMessage());
        assertEquals("error.account_not_active", ex.getMessageKey());
        assertArrayEquals(new Object[]{"TR290006200000000000000111"}, ex.getArgs());
    }

    @Test
    void shouldBeBusinessException() {
        AccountNotActiveException ex = new AccountNotActiveException("TR111111111111111111111111");
        assertInstanceOf(BusinessException.class, ex);
    }

    @Test
    void shouldReturnCorrectErrorCode() {
        AccountNotActiveException ex = new AccountNotActiveException("TR111111111111111111111111");
        assertEquals("ACCOUNT_NOT_ACTIVE", ex.getErrorCode());
    }
}
