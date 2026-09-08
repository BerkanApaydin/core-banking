package com.bank.app.infrastructure.adapter.in.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenBlacklistPropertiesTest {

    @Test
    void shouldApplyConfiguredBounds() {
        TokenBlacklistProperties props = new TokenBlacklistProperties(1_000L, 2_592_000_000L);
        assertThat(props.minTtlMs()).isEqualTo(1_000L);
        assertThat(props.maxTtlMs()).isEqualTo(2_592_000_000L);
    }

    @Test
    void shouldRejectNonPositiveMinTtl() {
        assertThatThrownBy(() -> new TokenBlacklistProperties(0L, 2_592_000_000L))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectMaxTtlBelowMinTtl() {
        assertThatThrownBy(() -> new TokenBlacklistProperties(60_000L, 1_000L))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }
}
