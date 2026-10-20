package com.bank.app.infrastructure.adapter.in.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
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
        /**
         * Threshold of the ACCOUNT-only budget (no IP dimension). It is
         * deliberately much higher than {@code maxAttempts}: the account+IP
         * budget is the tight one, and this one only exists to stop a
         * distributed attack that spreads attempts over many IPs. Keeping it
         * low would let one abusive IP lock a victim out of their own
         * account (a cheap denial of service).
         */
        @DefaultValue("50") int globalAccountMaxAttempts,
        @DefaultValue("15") long windowMinutes,
        // Audit: the Caffeine counter-cache bound was a magic literal (10000,
        // twice) in CaffeineLoginAttemptAdapter. Sized for peak tracked
        // IPs/usernames per window; eviction is time-based, this is only a
        // memory guard.
        @DefaultValue("10000") int cacheMaxSize
) {
    @ConstructorBinding
    public LoginAttemptProperties {
        if (windowMinutes <= 0 || windowMinutes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Failed-login window must be a positive number of minutes");
        }
        if (cacheMaxSize <= 0) {
            throw new IllegalArgumentException("Failed-login cache size must be positive");
        }
        if (globalAccountMaxAttempts < 0) {
            throw new IllegalArgumentException(
                    "Failed-login global account threshold must not be negative");
        }
    }

    /**
     * Convenience constructor for callers that do not tune the account-only
     * budget: it keeps the default multiplier of the per-IP threshold.
     */
    public LoginAttemptProperties(int maxAttempts, long windowMinutes, int cacheMaxSize) {
        this(maxAttempts, defaultGlobalAccountThreshold(maxAttempts), windowMinutes, cacheMaxSize);
    }

    /**
     * The account-only threshold is ten times the per-IP threshold, but it
     * must never overflow: a caller that disabled the guard (a negative
     * maxAttempts) or set an extreme threshold would otherwise wrap into a
     * negative value and fail the whole configuration.
     */
    private static int defaultGlobalAccountThreshold(int maxAttempts) {
        if (maxAttempts <= 0) {
            return 10;
        }
        long threshold = (long) maxAttempts * 10L;
        return (int) Math.min(threshold, Integer.MAX_VALUE);
    }
}
