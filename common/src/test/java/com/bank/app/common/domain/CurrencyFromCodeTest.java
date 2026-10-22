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

    @Test
    @DisplayName("should normalise case and surrounding whitespace")
    void shouldNormaliseCaseAndWhitespace() {
        assertThat(Currency.fromCode("try")).isEqualTo(Currency.TRY);
        assertThat(Currency.fromCode("TRY")).isEqualTo(Currency.TRY);
        assertThat(Currency.fromCode("TrY")).isEqualTo(Currency.TRY);
        assertThat(Currency.fromCode(" try ")).isEqualTo(Currency.TRY);
    }

    @Test
    @DisplayName("should reject a code with embedded whitespace")
    void shouldRejectEmbeddedWhitespace() {
        // Trimming is applied to the ends only: an inner blank must not be
        // silently dropped, or a mangled code could map to a currency.
        assertThatThrownBy(() -> Currency.fromCode(" tr y "))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessageContaining("Unsupported currency");
    }
}
