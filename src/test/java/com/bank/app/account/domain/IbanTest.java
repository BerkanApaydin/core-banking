package com.bank.app.account.domain;

import com.bank.app.common.exception.InvalidIbanException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IbanTest {

    @Test
    void shouldCreateIbanWhenFormatIsValid() {
        String validIbanStr = "TR29 0006 2000 0000 0000 0001 11";
        Iban iban = new Iban(validIbanStr);
        assertEquals("TR290006200000000000000111", iban.value());
    }

    @Test
    void shouldThrowInvalidIbanExceptionWhenFormatIsInvalid() {
        assertThrows(InvalidIbanException.class, () -> new Iban("TR29000"));
        assertThrows(InvalidIbanException.class, () -> new Iban("US290006200000000000000111"));
        assertThrows(InvalidIbanException.class, () -> new Iban("TR29000620000000000000011A"));
    }
}
