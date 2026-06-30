package com.bank.app.account.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.InvalidIbanException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class InvalidIbanExceptionTest {

    @Test
    void shouldCreateWithMessage() {
        InvalidIbanException ex = new InvalidIbanException("Invalid IBAN: XYZ");
        assertEquals("Invalid IBAN: XYZ", ex.getMessage());
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
