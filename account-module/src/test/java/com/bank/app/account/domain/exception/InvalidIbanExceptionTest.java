package com.bank.app.account.domain.exception;

import com.bank.app.common.exception.BusinessException;
import com.bank.app.common.exception.InvalidIbanException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InvalidIbanExceptionTest {

    @Test
    void shouldCreateWithMessage() {
        InvalidIbanException ex = new InvalidIbanException("Geçersiz IBAN: XYZ");
        assertEquals("Geçersiz IBAN: XYZ", ex.getMessage());
        assertNull(ex.getMessageKey());
        assertNull(ex.getArgs());
    }

    @Test
    void shouldBeBusinessException() {
        InvalidIbanException ex = new InvalidIbanException("test");
        assertInstanceOf(BusinessException.class, ex);
    }

    @Test
    void shouldDeriveErrorCodeFromClassName() {
        InvalidIbanException ex = new InvalidIbanException("test");
        assertEquals("INVALID_IBAN", ex.getErrorCode());
    }
}
