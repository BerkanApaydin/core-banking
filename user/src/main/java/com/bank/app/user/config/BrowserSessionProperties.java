package com.bank.app.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Typed browser-session cookie configuration owned by the User bounded
 * context. Replaces the scattered {@code @Value} placeholder in
 * {@code BrowserAuthController} so the secure-cookie flag is bound,
 * defaulted and documented in one place.
 *
 * <p>Kept in {@code user} (not {@code infrastructure}) on purpose: bounded
 * contexts must not depend on infrastructure-owned configuration types
 * (module-boundary rule). {@code JwtProperties} stays the owner of the
 * {@code jwt.*} namespace; token lifetimes needed here are bound by the
 * sibling {@code SessionTokenLifetimeProperties}.
 */
@ConfigurationProperties(prefix = "app.security.browser-session")
public record BrowserSessionProperties(
        @DefaultValue("false") boolean secure
) {
}
