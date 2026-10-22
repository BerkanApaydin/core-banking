package com.bank.app.infrastructure.adapter.in.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LoginAttemptProperties")
class LoginAttemptPropertiesTest {

    @Test
    @DisplayName("should bind explicit values")
    void shouldBindExplicitValues() {
        LoginAttemptProperties props = new LoginAttemptProperties(5, 15L, 10000);

        assertThat(props.maxAttempts()).isEqualTo(5);
        assertThat(props.windowMinutes()).isEqualTo(15L);
    }

    @Test
    @DisplayName("should allow negative maxAttempts as the disabled-guard sentinel")
    void shouldAllowNegativeMaxAttempts() {
        // Both adapters short-circuit to not-blocked when maxAttempts < 0;
        // the record must not reject the sentinel the adapters document.
        LoginAttemptProperties props = new LoginAttemptProperties(-1, 15L, 10000);

        assertThat(props.maxAttempts()).isEqualTo(-1);
    }

    @Test
    @DisplayName("should derive the global threshold for zero and negative sentinels")
    void shouldDeriveGlobalThresholdForDisabledSentinel() {
        assertThat(new LoginAttemptProperties(0, 15L, 10000).globalAccountMaxAttempts())
                .isEqualTo(10);
        assertThat(new LoginAttemptProperties(-5, 15L, 10000).globalAccountMaxAttempts())
                .isEqualTo(10);
    }

    @Test
    @DisplayName("should scale the global threshold for positive attempts")
    void shouldScaleGlobalThreshold() {
        assertThat(new LoginAttemptProperties(7, 15L, 10000).globalAccountMaxAttempts())
                .isEqualTo(70);
    }

    @Test
    @DisplayName("should reject non-positive windows because counters must expire")
    void shouldRejectNonPositiveWindow() {
        assertThatThrownBy(() -> new LoginAttemptProperties(5, 0, 10000))
                .isExactlyInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginAttemptProperties(5, -1, 10000))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject windows the counter backends cannot represent")
    void shouldRejectOversizedWindow() {
        assertThatThrownBy(() -> new LoginAttemptProperties(5, (long) Integer.MAX_VALUE + 1, 10000))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject non-positive cache and negative global threshold")
    void shouldRejectInvalidCacheAndGlobalThreshold() {
        // A zero-sized Caffeine cache would evict on every put, silently
        // disabling the guard; a negative account budget is never valid.
        assertThatThrownBy(() -> new LoginAttemptProperties(5, 15L, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cache size");
        assertThatThrownBy(() -> new LoginAttemptProperties(5, -1, 15L, 10000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("global account threshold");
    }

    @Test
    @DisplayName("should cap the derived global threshold instead of overflowing")
    void shouldCapDerivedGlobalThreshold() {
        assertThat(new LoginAttemptProperties(Integer.MAX_VALUE, 15L, 10000)
                .globalAccountMaxAttempts()).isEqualTo(Integer.MAX_VALUE);
    }
}
