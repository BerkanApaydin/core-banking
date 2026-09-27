package com.bank.app.account.domain;

import com.bank.app.common.domain.exception.InvalidIbanException;
import com.bank.app.common.domain.Iban;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class IbanTest {

    @Test
    void shouldThrowNullPointerExceptionWhenValueIsNull() {
        assertThrows(NullPointerException.class, () -> new Iban(null));
    }

    @Test
    void shouldCreateIbanWhenFormatIsValid() {
        String validIbanStr = "TR77 0006 2000 0000 0000 0001 11";
        Iban iban = new Iban(validIbanStr);
        assertEquals("TR770006200000000000000111", iban.value());
    }

    @Test
    void shouldThrowInvalidIbanExceptionWhenFormatIsInvalid() {
        InvalidIbanException ex1 = assertThrows(InvalidIbanException.class, () -> new Iban("TR29000"));
        assertEquals("Invalid IBAN format", ex1.getMessage());
        InvalidIbanException ex2 = assertThrows(InvalidIbanException.class,
                () -> new Iban("US290006200000000000000111"));
        assertEquals("Invalid IBAN format", ex2.getMessage());
        InvalidIbanException ex3 = assertThrows(InvalidIbanException.class,
                () -> new Iban("TR29000620000000000000011A"));
        assertEquals("Invalid IBAN format", ex3.getMessage());
    }
}
