package com.bank.app.common.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IbanLogMaskTest {

    @Test
    void masksFullLengthIban() {
        assertEquals("TR770006*******0111", IbanLogMask.mask("TR770006200000000000000111"));
    }

    @Test
    void normalizesBeforeMasking() {
        assertEquals("TR770006*******0111", IbanLogMask.mask("tr77 0006 2000 0000 0000 0001 11"));
    }

    @Test
    void collapsesShortOrInvalidValues() {
        assertEquals("***", IbanLogMask.mask("TR000"));
        assertEquals("***", IbanLogMask.mask("XYZ"));
        assertEquals("***", IbanLogMask.mask(""));
        assertEquals("***", IbanLogMask.mask((String) null));
    }

    @Test
    void masksValueObject() {
        assertEquals("TR770006*******0111",
                IbanLogMask.mask(new Iban("TR770006200000000000000111")));
        assertEquals("***", IbanLogMask.mask((Iban) null));
    }

    @Test
    void matchesIbanToString() {
        Iban iban = new Iban("TR440006200000000000000123");
        assertEquals(iban.toString(), IbanLogMask.mask(iban));
        assertEquals(iban.toString(), IbanLogMask.mask("TR440006200000000000000123"));
    }
}
