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
}
