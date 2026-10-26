package com.bank.app.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionSafetyConfigTest {

    @Test
    void rejectsUnsafeProductionBeforeAnyApplicationSingletonStarts() {
        AtomicBoolean singletonStarted = new AtomicBoolean();
        new ApplicationContextRunner()
                .withUserConfiguration(ProductionSafetyConfig.class)
                // Override any JWT_SECRET exported by CI so this test always checks
                // the JWT guard, not the next production configuration guard.
                .withPropertyValues("spring.profiles.active=prod", "jwt.secret=")
                .withBean("applicationWorker", Object.class, () -> {
                    singletonStarted.set(true);
                    return new Object();
                })
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("non-default JWT secret");
                    assertThat(singletonStarted).isFalse();
                });
    }

    @Test
    void allowsSingletonInitializationAfterValidProductionChecks() {
        AtomicBoolean singletonStarted = new AtomicBoolean();
        new ApplicationContextRunner()
                .withUserConfiguration(ProductionSafetyConfig.class)
                .withPropertyValues("spring.profiles.active=prod",
                        "jwt.secret=non-default-secret", "spring.datasource.password=non-default-password",
                        "app.security.token-blacklist.backend=database",
                        "app.security.browser-session.secure=true",
                        "app.security.cors.allowed-origins=https://bank.example.com",
                        "app.security.failed-login.backend=redis", "app.security.rate-limit.backend=redis",
                        "app.cache.caffeine.account-info.backend=redis",
                        // Silent-floor settings must be PRESENT in prod: the
                        // base yml defines them, so this context (which does
                        // not load application.yml) pins them explicitly.
                        "app.security.password-encoder-strength=12",
                        "app.security.hsts-max-age-seconds=31536000",
                        "app.security.mfa.encryption-key-base64=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=")
                .withBean("applicationWorker", Object.class, () -> {
                    singletonStarted.set(true);
                    return new Object();
                })
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(singletonStarted).isTrue();
                });
    }
}
