package com.bank.app.infrastructure.adapter.in.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Typed brute-force guard configuration (replaces the scattered
 * {@code @Value} placeholders in the login-attempt adapters). Bound,
 * defaulted and validated in one place; both the Caffeine and Redis
 * backends read the same bean, so the two can never silently diverge.
 *
 * <p>A negative {@code maxAttempts} is a documented sentinel meaning "guard
 * disabled" (both adapters short-circuit to not-blocked) — deliberately not
 * rejected here. The window must stay a positive number of minutes the
 * counter backends can represent.
 */
@Validated
@ConfigurationProperties(prefix = "app.security.failed-login")
public record LoginAttemptProperties(
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("15") long windowMinutes
) {
    public LoginAttemptProperties {
        if (windowMinutes <= 0 || windowMinutes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Failed-login window must be a positive number of minutes");
        }
    }
}
