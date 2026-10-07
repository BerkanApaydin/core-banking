package com.bank.app.infrastructure.adapter.in.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Typed JWT configuration (D13/K14): replaces the scattered {@code @Value}
 * placeholders so the security-critical settings are bound, defaulted and
 * validated in one place. Fail-fast on blank secret — never boot unsigned.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("900000") long accessExpiration,
        @DefaultValue("604800000") long refreshExpiration,
        @DefaultValue("false") boolean allowDefaultSecret
) {
    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException(
                "JWT secret must not be blank. Set JWT_SECRET environment variable. "
                + "Generate a secure key with: openssl rand -base64 32");
        }
        if (accessExpiration <= 0) {
            throw new IllegalArgumentException("JWT access expiration must be positive");
        }
        if (refreshExpiration <= 0) {
            throw new IllegalArgumentException("JWT refresh expiration must be positive");
        }
    }
}
