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

            assertThat(props.whitelistPaths()).containsExactly(
                    "/api/v1/auth/**", "/v3/api-docs/**", "/swagger-ui/**",
                    "/swagger-ui.html", "/actuator/health/**", "/", "/index.html",
                    "/app.js", "/style.css", "/favicon.ico", "/error"
            );
        }

        @Test
        @DisplayName("should use default paths when whitelistPaths is empty")
        void shouldUseDefaultsWhenEmpty() {
            SecurityProperties props = new SecurityProperties(List.of());

            assertThat(props.whitelistPaths()).containsExactly(
                    "/api/v1/auth/**", "/v3/api-docs/**", "/swagger-ui/**",
                    "/swagger-ui.html", "/actuator/health/**", "/", "/index.html",
                    "/app.js", "/style.css", "/favicon.ico", "/error"
            );
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
