package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.infrastructure.adapter.out.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtProperties")
class JwtPropertiesTest {

    private static final String SECRET = "KqppTj5E0Ofnmy0Zqpes4lcblwsqf50J7huOCLOjsYE=";

    @Test
    @DisplayName("should bind explicit values (D13/K14)")
    void shouldBindExplicitValues() {
        JwtProperties props = new JwtProperties(SECRET, 900000L, 604800000L, false);

        assertThat(props.secret()).isEqualTo(SECRET);
        assertThat(props.accessExpiration()).isEqualTo(900000L);
        assertThat(props.refreshExpiration()).isEqualTo(604800000L);
        assertThat(props.allowDefaultSecret()).isFalse();
    }

    @Test
    @DisplayName("should fail fast on blank secret (D13/K14)")
    void shouldFailFastOnBlankSecret() {
        assertThatThrownBy(() -> new JwtProperties("   ", 900000L, 604800000L, false))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT secret must not be blank");
        assertThatThrownBy(() -> new JwtProperties(null, 900000L, 604800000L, false))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject non-positive expirations (D13/K14)")
    void shouldRejectNonPositiveExpirations() {
        assertThatThrownBy(() -> new JwtProperties(SECRET, 0L, 604800000L, false))
                .isExactlyInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties(SECRET, 900000L, -1L, false))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("provider should accept typed properties (D13/K14)")
    void providerShouldAcceptTypedProperties() {
        JwtTokenProvider provider =
                new JwtTokenProvider(
                        new JwtProperties(SECRET, 900000L, 604800000L, true));

        assertThat(provider.getExpirationMs()).isEqualTo(900000L);
        assertThat(provider.getRefreshExpirationMs()).isEqualTo(604800000L);
    }
}
