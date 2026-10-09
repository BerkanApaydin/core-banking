package com.bank.app.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Token lifetimes needed by the browser-session adapter, owned by the User
 * bounded context. Binds the same {@code jwt.*} keys the infrastructure-owned
 * {@code JwtProperties} binds, but only the two lifetime fields — never the
 * secret. The secret stays exclusively owned by {@code JwtProperties}, so a
 * user-module change can neither weaken secret validation nor leak the key
 * into a second binding site.
 *
 * <p>Sharing the {@code jwt} prefix with {@code JwtProperties} is safe: both
 * records are registered as distinct beans and Spring binds each
 * independently, ignoring unknown fields.
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record SessionTokenLifetimeProperties(
        @DefaultValue("900000") long accessExpiration,
        @DefaultValue("604800000") long refreshExpiration
) {
    public SessionTokenLifetimeProperties {
        if (accessExpiration <= 0) {
            throw new IllegalArgumentException("JWT access expiration must be positive");
        }
        if (refreshExpiration <= 0) {
            throw new IllegalArgumentException("JWT refresh expiration must be positive");
        }
    }
}
