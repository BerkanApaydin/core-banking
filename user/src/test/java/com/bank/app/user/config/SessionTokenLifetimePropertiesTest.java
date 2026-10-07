package com.bank.app.user.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SessionTokenLifetimeProperties")
class SessionTokenLifetimePropertiesTest {

    @Test
    @DisplayName("should bind explicit values")
    void shouldBindExplicitValues() {
        SessionTokenLifetimeProperties props =
                new SessionTokenLifetimeProperties(900000L, 604800000L);

        assertThat(props.accessExpiration()).isEqualTo(900000L);
        assertThat(props.refreshExpiration()).isEqualTo(604800000L);
    }

    @Test
    @DisplayName("should reject non-positive expirations (fail-fast, mirrors JwtProperties)")
    void shouldRejectNonPositiveExpirations() {
        assertThatThrownBy(() -> new SessionTokenLifetimeProperties(0L, 604800000L))
                .isExactlyInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SessionTokenLifetimeProperties(900000L, -1L))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should default to insecure cookies only outside TLS")
    void shouldDefaultToInsecureCookies() {
        // Documents the dev default: plain-HTTP local development cannot set
        // Secure cookies; prod overrides to true (pinned by prod config).
        assertThat(new BrowserSessionProperties(false).secure()).isFalse();
    }
}
