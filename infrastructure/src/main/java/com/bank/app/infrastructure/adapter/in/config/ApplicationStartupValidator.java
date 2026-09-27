package com.bank.app.infrastructure.adapter.in.config;

import com.bank.app.infrastructure.adapter.out.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

public class ApplicationStartupValidator {

    private static final Logger log = LoggerFactory.getLogger(ApplicationStartupValidator.class);

    // Single-sourced from JwtTokenProvider: one literal, no silent divergence.
    private static final String DEFAULT_JWT_SECRET = JwtTokenProvider.DEFAULT_JWT_SECRET;

    private final Environment environment;

    public ApplicationStartupValidator(Environment environment) {
        this.environment = environment;
    }

    public void validateProductionConfig() {
        String jwtSecret = environment.getProperty("jwt.secret", DEFAULT_JWT_SECRET);
        String dbPassword = environment.getProperty("spring.datasource.password", "");

        if (!isProdProfile()) {
            if (DEFAULT_JWT_SECRET.equals(jwtSecret)) {
                log.warn("Default JWT secret is being used in non-prod environment. "
                        + "This is acceptable for local development but should never be used in production.");
            }
            return;
        }

        if (environment.acceptsProfiles(Profiles.of("dev", "demo", "test", "testcontainers"))) {
            throw new IllegalStateException("Production profile must not be combined with development or test profiles");
        }

        if (jwtSecret == null || jwtSecret.isBlank() || DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "Production profile requires a non-default JWT secret. Set JWT_SECRET environment variable.");
        }
        if (dbPassword == null || dbPassword.isBlank()) {
            throw new IllegalStateException(
                    "Production profile requires a database password via environment variable.");
        }
        if ("bank_password".equals(dbPassword)) {
            throw new IllegalStateException(
                    "Production profile must not use the default database password. "
                    + "Set DB_PASSWORD environment variable to a secure password.");
        }
        String blacklistBackend = environment.getProperty("app.security.token-blacklist.backend", "");
        if (!"hybrid".equals(blacklistBackend) && !"database".equals(blacklistBackend)) {
            throw new IllegalStateException(
                    "Production token revocations require the hybrid or database backend");
        }
        if (!environment.getProperty("app.security.browser-session.secure", Boolean.class, false)) {
            throw new IllegalStateException("Production browser sessions require Secure cookies");
        }
        requireRedisBackend("app.security.failed-login.backend");
        requireRedisBackend("app.security.rate-limit.backend");
        requirePositive("app.security.failed-login.max-attempts", 5L);
        requirePositive("app.security.failed-login.window-minutes", 15L);
        requirePositive("app.security.rate-limit.max-requests", 10L);
        requirePositive("app.security.rate-limit.time-window-ms", 10_000L);
    }

    private void requireRedisBackend(String property) {
        if (!"redis".equals(environment.getProperty(property))) {
            throw new IllegalStateException("Production requires a shared Redis backend: " + property);
        }
    }

    private void requirePositive(String property, long defaultValue) {
        if (environment.getProperty(property, Long.class, defaultValue) <= 0) {
            throw new IllegalStateException("Production requires a positive value: " + property);
        }
    }

    private boolean isProdProfile() {
        return environment.acceptsProfiles(Profiles.of("prod"));
    }
}
