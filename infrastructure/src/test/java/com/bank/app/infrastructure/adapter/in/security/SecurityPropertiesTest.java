package com.bank.app.infrastructure.adapter.in.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecurityProperties")
class SecurityPropertiesTest {

    @Nested
    @DisplayName("constructor defaults")
    class ConstructorDefaults {

        @Test
        @DisplayName("should use default paths when whitelistPaths is null")
        void shouldUseDefaultsWhenNull() {
            SecurityProperties props = new SecurityProperties(null);

            assertThat(props.whitelistPaths()).containsExactlyElementsOf(SecurityProperties.DEFAULT_WHITELIST);
            assertThat(props.whitelistPaths()).contains(
                    "/api/v1/auth/login", "/api/v1/auth/browser/login", "/api/v1/auth/register", "/api/v1/auth/refresh", "/api/v1/auth/browser/refresh",
                    "/v3/api-docs/**", "/swagger-ui/**",
                    "/swagger-ui.html", "/actuator/health/**", "/", "/index.html",
                    "/*.js", "/*.css", "/assets/**",
                    "/style.css", "/favicon.ico", "/error"
            );
        }

        @Test
        @DisplayName("should stay empty when whitelistPaths is explicitly empty (fail-closed K2/D2)")
        void shouldStayEmptyWhenExplicitlyEmpty() {
            SecurityProperties props = new SecurityProperties(List.of());

            assertThat(props.whitelistPaths()).isEmpty();
        }

        @Test
        @DisplayName("default whitelist must use asset patterns instead of enumerated files (6.3)")
        void defaultWhitelistMustUseAssetPatterns() {
            SecurityProperties props = new SecurityProperties(null);

            assertThat(props.whitelistPaths()).contains("/*.js", "/*.css", "/assets/**");
            assertThat(props.whitelistPaths()).doesNotContain("/app.js", "/boot.js", "/accounts.js");
        }

        @Test
        @DisplayName("default whitelist must NOT expose prometheus; only explicit prod config may (K1/D1)")
        void defaultWhitelistMustNotExposePrometheus() {
            SecurityProperties props = new SecurityProperties(null);

            assertThat(props.whitelistPaths()).doesNotContain("/actuator/prometheus");
            assertThat(props.whitelistPaths()).contains("/actuator/health/**");
        }
    }

    @Nested
    @DisplayName("custom paths")
    class CustomPaths {

        @Test
        @DisplayName("should use provided custom paths")
        void shouldUseCustomPaths() {
            SecurityProperties props = new SecurityProperties(List.of("/custom/**"));

            assertThat(props.whitelistPaths()).containsExactly("/custom/**");
        }
    }
}
