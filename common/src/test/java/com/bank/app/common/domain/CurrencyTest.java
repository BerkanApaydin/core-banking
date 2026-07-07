package com.bank.app.common.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("null")
class CurrencyTest {

    @ParameterizedTest
    @EnumSource(Currency.class)
    void shouldSupportRoundTripViaValueOf(Currency currency) {
        assertThat(Currency.valueOf(currency.name())).isEqualTo(currency);
    }

    @Test
    void shouldHaveExactlyThreeCurrencies() {
        assertThat(Currency.values()).hasSize(3);
    }
}
