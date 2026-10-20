package com.bank.app.infrastructure.adapter.in.config;

import org.springframework.core.env.Environment;

/**
 * Numeric startup-requirement helpers shared by the validators.
 */
final class StartupPropertyRules {

    private final Environment environment;

    StartupPropertyRules(Environment environment) {
        this.environment = environment;
    }

    void requirePositive(String property, long defaultValue) {
        if (environment.getProperty(property, Long.class, defaultValue) <= 0) {
            throw new IllegalStateException("Production requires a positive value: " + property);
        }
    }

    /**
     * Requires a value of at least {@code minimum}. Used for settings whose
     * weakening is silent (BCrypt cost, HSTS max-age): a lower value does not
     * fail anywhere, it just makes the deployment weaker.
     *
     * <p>An ABSENT key fails as well: the base configuration defines both
     * keys, so a missing value means the property was renamed or dropped —
     * treating that as "the floor" would quietly accept a deployment that
     * no longer pins the setting at all.
     */
    void requireAtLeast(String property, long minimum) {
        Long value = environment.getProperty(property, Long.class);
        if (value == null) {
            throw new IllegalStateException(
                    "Production requires " + property + " >= " + minimum + " (not set)");
        }
        if (value < minimum) {
            throw new IllegalStateException(
                    "Production requires " + property + " >= " + minimum + " (found: " + value + ")");
        }
    }

    void requireNonNegative(String property, long defaultValue) {
        if (environment.getProperty(property, Long.class, defaultValue) < 0) {
            throw new IllegalStateException("Production requires a non-negative value: " + property);
        }
    }
}
