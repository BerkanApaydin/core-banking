package com.bank.app.common.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("null")
class CurrencyTest {

    @Test
    void shouldHaveTryCurrency() {
        assertThat(Currency.TRY.name()).isEqualTo("TRY");
    }

    @Test
    void shouldHaveUsdCurrency() {
        assertThat(Currency.USD.name()).isEqualTo("USD");
    }

    @Test
    void shouldHaveEurCurrency() {
        assertThat(Currency.EUR.name()).isEqualTo("EUR");
    }

    @Test
    void shouldHaveThreeValues() {
        assertThat(Currency.values()).hasSize(3);
    }
}
