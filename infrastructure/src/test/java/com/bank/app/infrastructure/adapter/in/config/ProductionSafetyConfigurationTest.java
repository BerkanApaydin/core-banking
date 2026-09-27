package com.bank.app.infrastructure.adapter.in.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionSafetyConfigurationTest {

    @Test
    void rejectsUnsafeProductionBeforeAnyApplicationSingletonStarts() {
        AtomicBoolean singletonStarted = new AtomicBoolean();
        new ApplicationContextRunner()
                .withUserConfiguration(ProductionSafetyConfiguration.class)
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
                .withUserConfiguration(ProductionSafetyConfiguration.class)
                .withPropertyValues("spring.profiles.active=prod",
                        "jwt.secret=non-default-secret", "spring.datasource.password=non-default-password",
                        "app.security.token-blacklist.backend=database",
                        "app.security.browser-session.secure=true",
                        "app.security.failed-login.backend=redis", "app.security.rate-limit.backend=redis")
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
