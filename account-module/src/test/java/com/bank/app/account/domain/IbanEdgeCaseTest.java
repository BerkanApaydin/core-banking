package com.bank.app.account.domain;

import com.bank.app.common.exception.InvalidIbanException;
import com.bank.app.common.domain.Iban;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IbanEdgeCaseTest {

    @Test
    void shouldNormalizeIbanWithSpaces() {
        Iban iban = new Iban("TR29 0006 2000 0000 0000 0001 11");
        assertEquals("TR290006200000000000000111", iban.value());
    }

    @Test
    void shouldNormalizeIbanWithLowercase() {
        Iban iban = new Iban("tr290006200000000000000111");
        assertEquals("TR290006200000000000000111", iban.value());
    }

    @Test
    void shouldNormalizeIbanWithMixedCaseAndSpaces() {
        Iban iban = new Iban("Tr29 0006 2000 0000 0000 0001 11");
        assertEquals("TR290006200000000000000111", iban.value());
    }

    @Test
    void shouldThrowWhenIbanIsTooShort() {
        assertThrows(InvalidIbanException.class, () -> new Iban("TR29"));
    }

    @Test
    void shouldThrowWhenIbanHasNonDigitCharacters() {
        assertThrows(InvalidIbanException.class,
                () -> new Iban("TR2900062000000000000001AB"));
    }

    @Test
    void shouldThrowWhenIbanDoesNotStartWithTR() {
        InvalidIbanException ex = assertThrows(InvalidIbanException.class,
                () -> new Iban("US290006200000000000000111"));
        assertTrue(ex.getMessage().contains("Geçersiz IBAN formatı"));
    }

    @Test
    void shouldThrowWhenIbanIsWhitespace() {
        InvalidIbanException ex = assertThrows(InvalidIbanException.class,
                () -> new Iban("   "));
        assertTrue(ex.getMessage().contains("Geçersiz IBAN formatı"));
    }

    @Test
    void shouldThrowWhenIbanIsEmpty() {
        assertThrows(InvalidIbanException.class, () -> new Iban(""));
    }

    @Test
    void shouldThrowWhenIbanIsNull() {
        assertThrows(NullPointerException.class, () -> new Iban(null));
    }
}
