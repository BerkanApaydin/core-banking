package com.bank.app.common.domain;

import com.bank.app.common.domain.exception.InvalidIbanException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class IbanTest {

    @Test
    void shouldCreateWithValidIban() {
        Iban iban = new Iban("TR290006200000000000000123");
        assertEquals("TR290006200000000000000123", iban.value());
    }

    @Test
    void shouldNormalizeIbanWithSpaces() {
        Iban iban = new Iban("TR29 0006 2000 0000 0000 0001 23");
        assertEquals("TR290006200000000000000123", iban.value());
    }

    @Test
    void shouldNormalizeIbanToUpperCase() {
        Iban iban = new Iban("tr290006200000000000000123");
        assertEquals("TR290006200000000000000123", iban.value());
    }

    @Test
    void shouldThrowWhenNull() {
        assertThrows(NullPointerException.class, () -> new Iban(null));
    }

    @Test
    void shouldThrowWhenInvalidFormat() {
        assertThrows(InvalidIbanException.class, () -> new Iban("INVALID"));
    }

    @Test
    void shouldThrowWhenTooShort() {
        assertThrows(InvalidIbanException.class, () -> new Iban("TR12"));
    }

    @Test
    void shouldBeEqualWhenSameIban() {
        assertEquals(new Iban("TR290006200000000000000123"), new Iban("TR290006200000000000000123"));
    }

    @Test
    void shouldNotBeEqualWhenDifferentIban() {
        assertNotEquals(new Iban("TR290006200000000000000123"), new Iban("TR290006200000000000000456"));
    }

    @Test
    void toStringShouldMaskMiddleDigits() {
        Iban iban = new Iban("TR290006200000000000000123");
        String masked = iban.toString();
        assertTrue(masked.contains("*******"), "Maskelenmeli: " + masked);
        assertTrue(masked.startsWith("TR290006"), "İlk 8 karakter korunmalı: " + masked);
        assertTrue(masked.endsWith("0123"), "Son 4 karakter korunmalı: " + masked);
    }

    @Test
    void toStringShouldHandleShortIban() {
        Iban iban = new Iban("TR123456789012345678901234");
        String masked = iban.toString();
        assertTrue(masked.contains("*******"), "Maskelenmeli: " + masked);
    }

    @Test
    void normalizeShouldReturnNullWhenNull() {
        assertNull(Iban.normalize(null));
    }
}
