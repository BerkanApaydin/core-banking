package com.bank.app.account.domain.exception;

import com.bank.app.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateIbanExceptionTest {

    @Test
    void shouldCreateWithMessage() {
        DuplicateIbanException ex = new DuplicateIbanException("Bu IBAN zaten mevcut");
        assertEquals("Bu IBAN zaten mevcut", ex.getMessage());
        assertNull(ex.getMessageKey());
        assertNull(ex.getArgs());
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
}
