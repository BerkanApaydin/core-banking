package com.bank.app.infrastructure.adapter.in.security;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Typed JWT configuration (D13/K14): replaces the scattered {@code @Value}
 * placeholders so the security-critical settings are bound, defaulted and
 * validated in one place. Fail-fast on blank secret — never boot unsigned.
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("900000") long accessExpiration,
        @DefaultValue("604800000") long refreshExpiration,
        @DefaultValue("false") boolean allowDefaultSecret,
        @DefaultValue("clean-architecture-bank") String issuer,
        @DefaultValue("clean-architecture-bank-api") String audience,
        @DefaultValue("primary") String keyId,
        @DefaultValue("30") long clockSkewSeconds,
        Map<String, String> retiredKeys
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
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("JWT issuer must not be blank");
        }
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("JWT audience must not be blank");
        }
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("JWT key id must not be blank");
        }
        if (clockSkewSeconds < 0 || clockSkewSeconds > 300) {
            throw new IllegalArgumentException(
                "JWT clock skew must be between 0 and 300 seconds");
        }
        if (retiredKeys == null || retiredKeys.isEmpty()) {
            retiredKeys = Map.of();
        } else {
            retiredKeys.forEach((retiredKeyId, retiredSecret) -> {
                if (retiredKeyId == null || retiredKeyId.isBlank()) {
                    throw new IllegalArgumentException(
                        "JWT retired key id must not be blank");
                }
                if (retiredSecret == null || retiredSecret.isBlank()) {
                    throw new IllegalArgumentException(
                        "JWT retired secret must not be blank for key id: " + retiredKeyId);
                }
            });
            retiredKeys = Map.copyOf(retiredKeys);
        }
        if (retiredKeys.containsKey(keyId)) {
            throw new IllegalArgumentException(
                "JWT retired keys must not contain the active key id: " + keyId);
        }
    }
}
