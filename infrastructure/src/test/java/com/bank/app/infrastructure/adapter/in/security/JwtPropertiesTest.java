package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.infrastructure.adapter.out.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtProperties")
class JwtPropertiesTest {

    // Zero-entropy placeholder: valid shape, never flags secret scanners.
    // The real dev default lives only in JwtTokenProvider + compose.
    private static final String SECRET = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Test
    @DisplayName("should bind explicit values (D13/K14)")
    void shouldBindExplicitValues() {
        JwtProperties props = properties();

        assertThat(props.secret()).isEqualTo(SECRET);
        assertThat(props.accessExpiration()).isEqualTo(900000L);
        assertThat(props.refreshExpiration()).isEqualTo(604800000L);
        assertThat(props.allowDefaultSecret()).isFalse();
        assertThat(props.issuer()).isEqualTo("test-issuer");
        assertThat(props.audience()).isEqualTo("test-audience");
        assertThat(props.keyId()).isEqualTo("test-key");
        assertThat(props.clockSkewSeconds()).isEqualTo(30L);
    }

    @Test
    @DisplayName("should fail fast on blank secret (D13/K14)")
    void shouldFailFastOnBlankSecret() {
        assertThatThrownBy(() -> properties("   ", "test-issuer", "test-audience", "test-key", 30L))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT secret must not be blank");
        assertThatThrownBy(() -> properties(null, "test-issuer", "test-audience", "test-key", 30L))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject non-positive expirations (D13/K14)")
    void shouldRejectNonPositiveExpirations() {
        assertThatThrownBy(() -> new JwtProperties(
                SECRET, 0L, 604800000L, false,
                "test-issuer", "test-audience", "test-key", 30L))
                .isExactlyInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties(
                SECRET, 900000L, -1L, false,
                "test-issuer", "test-audience", "test-key", 30L))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should fail fast on blank issuer/audience/key id")
    void shouldFailFastOnBlankTokenIdentity() {
        assertThatThrownBy(() -> properties(SECRET, "   ", "test-audience", "test-key", 30L))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("issuer");
        assertThatThrownBy(() -> properties(SECRET, null, "test-audience", "test-key", 30L))
                .isExactlyInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(SECRET, "test-issuer", "", "test-key", 30L))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audience");
        assertThatThrownBy(() -> properties(SECRET, "test-issuer", null, "test-key", 30L))
                .isExactlyInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(SECRET, "test-issuer", "test-audience", "  ", 30L))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key id");
        assertThatThrownBy(() -> properties(SECRET, "test-issuer", "test-audience", null, 30L))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should bound clock skew to 0-300 seconds")
    void shouldBoundClockSkew() {
        assertThatThrownBy(() -> properties(SECRET, "test-issuer", "test-audience", "test-key", -1L))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clock skew");
        assertThatThrownBy(() -> properties(SECRET, "test-issuer", "test-audience", "test-key", 301L))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clock skew");
        assertThat(properties(SECRET, "test-issuer", "test-audience", "test-key", 0L)
                .clockSkewSeconds()).isZero();
        assertThat(properties(SECRET, "test-issuer", "test-audience", "test-key", 300L)
                .clockSkewSeconds()).isEqualTo(300L);
    }

    @Test
    @DisplayName("provider should accept typed properties (D13/K14)")
    void providerShouldAcceptTypedProperties() {
        JwtTokenProvider provider = new JwtTokenProvider(properties());

        assertThat(provider.getExpirationMs()).isEqualTo(900000L);
        assertThat(provider.getRefreshExpirationMs()).isEqualTo(604800000L);
    }

    private static JwtProperties properties() {
        return properties(SECRET, "test-issuer", "test-audience", "test-key", 30L);
    }

    private static JwtProperties properties(String secret, String issuer, String audience,
                                            String keyId, long clockSkewSeconds) {
        return new JwtProperties(secret, 900000L, 604800000L, false,
                issuer, audience, keyId, clockSkewSeconds);
    }
}
