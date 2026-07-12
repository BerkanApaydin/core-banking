package com.bank.app.infrastructure.adapter.in.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void shouldConfigureCorsWithProperties() {
        CorsProperties properties = new CorsProperties(List.of("http://allowed-origin.com"));
        CorsConfig corsConfig = new CorsConfig(properties);

        CorsConfigurationSource source = corsConfig.corsConfigurationSource();
        jakarta.servlet.http.HttpServletRequest request = org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
        org.mockito.Mockito.when(request.getAttribute(org.springframework.web.util.UrlPathHelper.PATH_ATTRIBUTE)).thenReturn("/");
        CorsConfiguration config = source.getCorsConfiguration(request);

        assertThat(config).isNotNull();
        assertThat(config.getAllowedOrigins()).containsExactly("http://allowed-origin.com");
        assertThat(config.getAllowedMethods()).containsExactly("GET", "POST", "PUT", "DELETE", "OPTIONS");
        assertThat(config.getAllowedHeaders()).containsExactly("Authorization", "Content-Type", "Idempotency-Key", "X-Requested-With");
        assertThat(config.getExposedHeaders()).containsExactly("X-Correlation-ID");
        assertThat(config.getAllowCredentials()).isTrue();
    }
}
