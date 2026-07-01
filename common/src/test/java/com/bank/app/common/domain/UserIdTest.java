package com.bank.app.common.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("null")
class UserIdTest {

    @Test
    void shouldCreateWithValidValue() {
        UserId userId = new UserId(42L);
        assertThat(userId.value()).isEqualTo(42L);
    }

    @Test
    void shouldThrowWhenValueIsNull() {
        assertThatThrownBy(() -> new UserId(null))
                .isExactlyInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowWhenValueIsZero() {
        assertThatThrownBy(() -> new UserId(0L))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void shouldThrowWhenValueIsNegative() {
        assertThatThrownBy(() -> new UserId(-1L))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void shouldBeEqualWhenSameValue() {
        assertThat(new UserId(1L)).isEqualTo(new UserId(1L));
    }

    @Test
    void shouldNotBeEqualWhenDifferentValue() {
        assertThat(new UserId(1L)).isNotEqualTo(new UserId(2L));
    }

    @Test
    void shouldHaveSameHashCodeWhenSameValue() {
        assertThat(new UserId(1L)).hasSameHashCodeAs(new UserId(1L));
    }

    @Test
    void shouldReturnStringRepresentation() {
        assertThat(new UserId(42L).toString()).isEqualTo("42");
    }
}
