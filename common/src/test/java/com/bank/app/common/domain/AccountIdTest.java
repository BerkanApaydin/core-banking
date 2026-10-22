package com.bank.app.common.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Account ids cross the wire as untrusted path/query values and are rebuilt at
 * the adapter edge, so the typed wrapper is the only guard against a negative
 * or zero id reaching a lookup. Mirror of {@link UserIdTest}, including the
 * mixed-argument-swap protection the record type exists for.
 */
@SuppressWarnings("null")
class AccountIdTest {

    @Test
    void shouldCreateWithValidValue() {
        AccountId accountId = new AccountId(42L);

        assertThat(accountId.value()).isEqualTo(42L);
    }

    @Test
    void shouldThrowWhenValueIsNull() {
        assertThatThrownBy(() -> new AccountId(null))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    void shouldThrowWhenValueIsZero() {
        assertThatThrownBy(() -> new AccountId(0L))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void shouldThrowWhenValueIsNegative() {
        assertThatThrownBy(() -> new AccountId(Long.MIN_VALUE))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void shouldBeEqualWhenSameValue() {
        assertThat(new AccountId(1L)).isEqualTo(new AccountId(1L));
    }

    @Test
    void shouldNotBeEqualWhenDifferentValue() {
        assertThat(new AccountId(1L)).isNotEqualTo(new AccountId(2L));
    }

    @Test
    void shouldNotEqualTheRawLongItWraps() {
        assertThat(new AccountId(1L)).isNotEqualTo(1L);
    }

    @Test
    void shouldHaveSameHashCodeWhenSameValue() {
        assertThat(new AccountId(1L)).hasSameHashCodeAs(new AccountId(1L));
    }

    @Test
    void shouldReturnStringRepresentation() {
        assertThat(new AccountId(42L)).hasToString("42");
    }
}
