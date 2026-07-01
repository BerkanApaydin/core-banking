package com.bank.app.infrastructure.adapter.in.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IbanPropertiesTest {

    @Test
    void shouldHaveDefaultPattern() {
        IbanProperties props = new IbanProperties();
        assertEquals("^TR[0-9]{24}$", props.getPattern());
    }

    @Test
    void shouldSetAndGetPattern() {
        IbanProperties props = new IbanProperties();
        props.setPattern("^[A-Z]{2}[0-9]{2}[A-Z0-9]{1,30}$");
        assertEquals("^[A-Z]{2}[0-9]{2}[A-Z0-9]{1,30}$", props.getPattern());
    }
}
