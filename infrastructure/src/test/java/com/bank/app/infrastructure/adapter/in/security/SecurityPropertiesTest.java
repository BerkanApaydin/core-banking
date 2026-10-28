package com.bank.app.infrastructure.adapter.in.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SecurityProperties")
class SecurityPropertiesTest {

    @Nested
    @DisplayName("constructor defaults")
    class ConstructorDefaults {

        @Test
        @DisplayName("should use default paths when whitelistPaths is null")
        void shouldUseDefaultsWhenNull() {
            SecurityProperties props = new SecurityProperties(null, 12, 31536000L);

            assertThat(props.whitelistPaths()).containsExactlyElementsOf(SecurityProperties.DEFAULT_WHITELIST);
            assertThat(props.whitelistPaths()).contains(
                    "/api/v1/auth/login", "/api/v1/auth/browser/login", "/api/v1/auth/register", "/api/v1/auth/refresh", "/api/v1/auth/browser/refresh",
                    "/api/v1/auth/password/reset/request", "/api/v1/auth/password/reset/confirm",
                    "/v3/api-docs/**", "/swagger-ui/**",
                    "/swagger-ui.html", "/actuator/health/**", "/", "/index.html",
                    "/*.js", "/*.css", "/assets/**",
                    "/style.css", "/favicon.ico", "/error"
            );
        }

        @Test
        @DisplayName("should stay empty when whitelistPaths is explicitly empty (fail-closed K2/D2)")
        void shouldStayEmptyWhenExplicitlyEmpty() {
            SecurityProperties props = new SecurityProperties(List.of(), 12, 31536000L);

            assertThat(props.whitelistPaths()).isEmpty();
        }

        @Test
        @DisplayName("default whitelist must use asset patterns instead of enumerated files (6.3)")
        void defaultWhitelistMustUseAssetPatterns() {
            SecurityProperties props = new SecurityProperties(null, 12, 31536000L);

            assertThat(props.whitelistPaths()).contains("/*.js", "/*.css", "/assets/**");
            assertThat(props.whitelistPaths()).doesNotContain("/app.js", "/boot.js", "/accounts.js");
        }

        @Test
        @DisplayName("default whitelist must NOT expose prometheus; only explicit prod config may (K1/D1)")
        void defaultWhitelistMustNotExposePrometheus() {
            SecurityProperties props = new SecurityProperties(null, 12, 31536000L);

            assertThat(props.whitelistPaths()).doesNotContain("/actuator/prometheus");
            assertThat(props.whitelistPaths()).contains("/actuator/health/**");
        }
    }

    @Nested
    @DisplayName("validation bounds")
    class ValidationBounds {

        @Test
        @DisplayName("accepts the inclusive BCrypt strength range")
        void shouldAcceptBcryptBoundaries() {
            assertThat(new SecurityProperties(null, 4, 0).passwordEncoderStrength()).isEqualTo(4);
            assertThat(new SecurityProperties(null, 31, 0).passwordEncoderStrength()).isEqualTo(31);
        }

        @Test
        @DisplayName("rejects BCrypt strength outside [4, 31]")
        void shouldRejectBcryptOutOfRange() {
            // Below 4 is not a meaningful work factor; above 31 risks a login
            // CPU DoS and is not representable by the encoder.
            assertThatThrownBy(() -> new SecurityProperties(null, 3, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("BCrypt strength");
            assertThatThrownBy(() -> new SecurityProperties(null, 32, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("BCrypt strength");
        }

        @Test
        @DisplayName("accepts zero HSTS max-age but rejects negative")
        void shouldValidateHstsMaxAge() {
            assertThat(new SecurityProperties(null, 12, 0).hstsMaxAgeSeconds()).isZero();
            assertThatThrownBy(() -> new SecurityProperties(null, 12, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("HSTS max-age");
        }
    }

    @Nested
    @DisplayName("custom paths")
    class CustomPaths {

        @Test
        @DisplayName("should use provided custom paths")
        void shouldUseCustomPaths() {
            SecurityProperties props = new SecurityProperties(List.of("/custom/**"), 12, 31536000L);

            assertThat(props.whitelistPaths()).containsExactly("/custom/**");
        }

        @Test
        @DisplayName("should not expose the caller-owned whitelist list")
        void shouldDefensivelyCopyWhitelistPaths() {
            List<String> source = new ArrayList<>(List.of("/custom/**"));
            SecurityProperties props = new SecurityProperties(source, 12, 31536000L);

            source.add("/other/**");

            assertThat(props.whitelistPaths()).containsExactly("/custom/**");
            assertThatThrownBy(() -> props.whitelistPaths().add("/x"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
