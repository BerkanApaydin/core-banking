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

    @Test
    void shouldAllowZeroNegativeCacheTtlToDisableCaching() {
        // 0 is the documented "every request hits the database" switch.
        TokenBlacklistProperties props =
                new TokenBlacklistProperties(1_000L, 60_000L, 0L, 10_000L);

        assertThat(props.negativeCacheTtlMs()).isZero();
    }

    @Test
    void shouldRejectNegativeCacheTtlAndSize() {
        assertThatThrownBy(() -> new TokenBlacklistProperties(1_000L, 60_000L, -1L, 10_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative-cache TTL");
        assertThatThrownBy(() -> new TokenBlacklistProperties(1_000L, 60_000L, 5_000L, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative-cache size");
    }
}
