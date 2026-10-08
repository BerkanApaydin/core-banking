package com.bank.app.common.domain;

import com.bank.app.common.domain.exception.CurrencyMismatchException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Currency.fromCode")
class CurrencyFromCodeTest {

    @Test
    @DisplayName("should parse known codes case-insensitively")
    void shouldParseKnownCodes() {
        assertThat(Currency.fromCode("TRY")).isEqualTo(Currency.TRY);
        assertThat(Currency.fromCode("usd")).isEqualTo(Currency.USD);
        assertThat(Currency.fromCode(" eur ")).isEqualTo(Currency.EUR);
    }

    @Test
    @DisplayName("should throw domain exception for unknown codes")
    void shouldThrowForUnknown() {
        assertThatThrownBy(() -> Currency.fromCode("GBP"))
                .isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> Currency.fromCode(null))
                .isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> Currency.fromCode("  "))
                .isInstanceOf(CurrencyMismatchException.class);
    }
}
