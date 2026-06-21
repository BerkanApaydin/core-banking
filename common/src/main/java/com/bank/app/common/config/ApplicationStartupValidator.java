package com.bank.app.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class ApplicationStartupValidator {

    private static final Logger log = LoggerFactory.getLogger(ApplicationStartupValidator.class);

    private static final String DEFAULT_JWT_SECRET =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private final Environment environment;

    public ApplicationStartupValidator(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
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

        if (DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "Production profile requires a non-default JWT secret. Set JWT_SECRET environment variable.");
        }
        if (dbPassword.isBlank() || "bank_password".equals(dbPassword)) {
            throw new IllegalStateException(
                    "Production profile requires a secure database password via environment variable.");
        }
    }

    private boolean isProdProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }
}
