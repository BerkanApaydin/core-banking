package com.bank.app.common.domain.exception;

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
    void shouldReturnInvalidIbanErrorCode() {
        InvalidIbanException ex = new InvalidIbanException("test");
        assertEquals("INVALID_IBAN", ex.getErrorCode());
    }
}
