package com.bank.app.common.domain;

import com.bank.app.common.domain.exception.InvalidIbanException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("null")
class IbanTest {

    @Test
    void shouldCreateWithValidIban() {
        Iban iban = new Iban("TR290006200000000000000123");
        assertThat(iban.value()).isEqualTo("TR290006200000000000000123");
    }

    @Test
    void shouldNormalizeIbanWithSpaces() {
        Iban iban = new Iban("TR29 0006 2000 0000 0000 0001 23");
        assertThat(iban.value()).isEqualTo("TR290006200000000000000123");
    }

    @Test
    void shouldNormalizeIbanToUpperCase() {
        Iban iban = new Iban("tr290006200000000000000123");
        assertThat(iban.value()).isEqualTo("TR290006200000000000000123");
    }

    @Test
    void shouldThrowWhenNull() {
        assertThatThrownBy(() -> new Iban(null))
                .isExactlyInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowWhenInvalidFormat() {
        assertThatThrownBy(() -> new Iban("INVALID"))
                .isExactlyInstanceOf(InvalidIbanException.class);
    }

    @Test
    void shouldThrowWhenTooShort() {
        assertThatThrownBy(() -> new Iban("TR12"))
                .isExactlyInstanceOf(InvalidIbanException.class);
    }

    @Test
    void shouldBeEqualWhenSameIban() {
        assertThat(new Iban("TR290006200000000000000123"))
                .isEqualTo(new Iban("TR290006200000000000000123"));
    }

    @Test
    void shouldNotBeEqualWhenDifferentIban() {
        assertThat(new Iban("TR290006200000000000000123"))
                .isNotEqualTo(new Iban("TR290006200000000000000456"));
    }

    @Test
    void toStringShouldMaskMiddleDigits() {
        Iban iban = new Iban("TR290006200000000000000123");
        String masked = iban.toString();
        assertThat(masked).contains("*******")
                .startsWith("TR290006")
                .endsWith("0123");
    }

    @Test
    void toStringShouldHandleShortIban() {
        Iban iban = new Iban("TR123456789012345678901234");
        assertThat(iban.toString()).contains("*******");
    }

    @Test
    void normalizeShouldReturnNullWhenNull() {
        assertThat(Iban.normalize(null)).isNull();
    }
}
