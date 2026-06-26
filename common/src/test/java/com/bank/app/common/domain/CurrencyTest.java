package com.bank.app.common.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class CurrencyTest {

    @Test
    void shouldHaveTryCurrency() {
        assertEquals("TRY", Currency.TRY.name());
    }

    @Test
    void shouldHaveUsdCurrency() {
        assertEquals("USD", Currency.USD.name());
    }

    @Test
    void shouldHaveEurCurrency() {
        assertEquals("EUR", Currency.EUR.name());
    }

    @Test
    void shouldHaveThreeValues() {
        assertEquals(3, Currency.values().length);
    }
}
