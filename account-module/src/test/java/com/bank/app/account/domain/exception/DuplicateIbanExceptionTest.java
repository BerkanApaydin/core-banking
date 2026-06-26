package com.bank.app.account.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class DuplicateIbanExceptionTest {

    @Test
    void shouldCreateWithMessage() {
        DuplicateIbanException ex = new DuplicateIbanException("TR290006200000000000000123");
        assertEquals("Bu IBAN ile zaten bir hesap var: TR290006200000000000000123", ex.getMessage());
        assertEquals("error.duplicate_iban", ex.getMessageKey());
        assertArrayEquals(new Object[]{"TR290006200000000000000123"}, ex.getArgs());
    }

    @Test
    void shouldBeBusinessException() {
        DuplicateIbanException ex = new DuplicateIbanException("test");
        assertInstanceOf(BusinessException.class, ex);
    }

    @Test
    void shouldDeriveErrorCodeFromClassName() {
        DuplicateIbanException ex = new DuplicateIbanException("test");
        assertEquals("DUPLICATE_IBAN", ex.getErrorCode());
    }

    @Test
    void shouldCreateWithExplicitMessageKeyAndArgs() {
        DuplicateIbanException ex = new DuplicateIbanException("custom.key", new Object[]{"arg1"}, "Default message");
        assertEquals("custom.key", ex.getMessageKey());
        assertArrayEquals(new Object[]{"arg1"}, ex.getArgs());
        assertEquals("Default message", ex.getMessage());
    }
}
