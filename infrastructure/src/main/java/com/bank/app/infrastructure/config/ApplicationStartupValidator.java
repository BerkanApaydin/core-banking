package com.bank.app.infrastructure.config;

import com.bank.app.infrastructure.adapter.out.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Startup safety gate. Delegates to focused validators so each rule group
 * (backend selection, production requirements, CORS) has one reason to change.
 */
public class ApplicationStartupValidator {

    private static final Logger log = LoggerFactory.getLogger(ApplicationStartupValidator.class);

    // Single-sourced from JwtTokenProvider: one literal, no silent divergence.
    @SuppressWarnings("deprecation")
    private static final String DEFAULT_JWT_SECRET = JwtTokenProvider.DEFAULT_JWT_SECRET;

    private final Environment environment;
    private final BackendSelectionValidator backendSelectionValidator;
    private final ProductionConfigValidator productionConfigValidator;

    public ApplicationStartupValidator(Environment environment) {
        this.environment = environment;
        this.backendSelectionValidator = new BackendSelectionValidator(environment);
        this.productionConfigValidator = new ProductionConfigValidator(environment);
    }

    public void validateProductionConfig() {
        String jwtSecret = environment.getProperty("jwt.secret", DEFAULT_JWT_SECRET);

        // Backend selection is validated in EVERY profile, not just prod.
        backendSelectionValidator.validateAll();

        if (!isProdProfile()) {
            if (DEFAULT_JWT_SECRET.equals(jwtSecret)) {
                log.warn("Default JWT secret is being used in non-prod environment. "
                        + "This is acceptable for local development but should never be used in production.");
            }
            return;
        }

        productionConfigValidator.validate();
    }

    private boolean isProdProfile() {
        return environment.acceptsProfiles(Profiles.of("prod"));
    }
}
