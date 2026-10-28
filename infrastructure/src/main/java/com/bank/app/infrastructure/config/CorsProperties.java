package com.bank.app.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.util.List;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.cors")
public record CorsProperties(
        @DefaultValue({"http://localhost:3000", "http://127.0.0.1:3000", "http://localhost:8080"}) List<String> allowedOrigins
) {
    public CorsProperties {
        // Null and empty both mean "no origin is allowed" (fail-closed); the
        // canonical empty list keeps the record immutable on every path.
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
