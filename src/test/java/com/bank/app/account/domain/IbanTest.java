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
        InvalidIbanException ex1 = assertThrows(InvalidIbanException.class, () -> new Iban("TR29000"));
        assertEquals("Geçersiz IBAN formatı: TR29000", ex1.getMessage());
        InvalidIbanException ex2 = assertThrows(InvalidIbanException.class,
                () -> new Iban("US290006200000000000000111"));
        assertEquals("Geçersiz IBAN formatı: US290006200000000000000111", ex2.getMessage());
        InvalidIbanException ex3 = assertThrows(InvalidIbanException.class,
                () -> new Iban("TR29000620000000000000011A"));
        assertEquals("Geçersiz IBAN formatı: TR29000620000000000000011A", ex3.getMessage());
    }
}
