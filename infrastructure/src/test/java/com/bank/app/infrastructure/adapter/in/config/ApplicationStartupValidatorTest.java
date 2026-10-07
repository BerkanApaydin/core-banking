package com.bank.app.infrastructure.adapter.in.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationStartupValidatorTest {

    private MockEnvironment validProductionEnvironment() {
        return new MockEnvironment().withProperty("jwt.secret", "non-default-secret")
                .withProperty("spring.datasource.password", "non-default-password")
                .withProperty("app.security.token-blacklist.backend", "hybrid")
                .withProperty("app.security.browser-session.secure", "true")
                .withProperty("app.security.failed-login.backend", "redis")
                .withProperty("app.security.rate-limit.backend", "redis")
                .withProperty("spring.profiles.active", "prod");
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "local", "test"})
    void permitsExplicitNonProductionProfiles(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        assertThatCode(() -> new ApplicationStartupValidator(environment).validateProductionConfig())
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"hybrid", "database"})
    void acceptsDurableProductionBackends(String backend) {
        var environment = validProductionEnvironment()
                .withProperty("app.security.token-blacklist.backend", backend);
        assertThatCode(() -> new ApplicationStartupValidator(environment).validateProductionConfig())
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "jwt.secret | '' | non-default JWT secret",
            "jwt.secret | '   ' | non-default JWT secret",
            "jwt.secret | 404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970 | non-default JWT secret",
            "spring.datasource.password | '' | database password",
            "spring.datasource.password | bank_password | default database password",
            "app.security.token-blacklist.backend | redis | hybrid or database",
            "app.security.token-blacklist.backend | caffeine | hybrid or database",
            "app.security.browser-session.secure | false | Secure cookies",
            "app.security.failed-login.backend | caffeine | shared Redis",
            "app.security.rate-limit.backend | caffeine | shared Redis",
            "app.security.failed-login.max-attempts | 0 | positive value",
            "app.security.failed-login.window-minutes | -1 | positive value",
            "app.security.rate-limit.max-requests | 0 | positive value",
            "app.security.rate-limit.time-window-ms | -1 | positive value",
            "app.security.rate-limit.resource-max-requests | 0 | positive value",
            "app.security.rate-limit.resource-time-window-ms | -1 | positive value",
            "app.outbox.max-retries | 0 | positive value",
            "app.outbox.batch-size | -1 | positive value",
            "app.outbox.poll-delay-ms | 0 | positive value",
            "app.outbox.retention-days | 0 | positive value",
            "app.outbox.partition-count | -1 | non-negative value"
    })
    void rejectsUnsafeProductionOverrides(String property, String value, String reason) {
        var environment = validProductionEnvironment().withProperty(property, value);
        assertThatThrownBy(() -> new ApplicationStartupValidator(environment).validateProductionConfig())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(reason);
    }

    @Test
    void acceptsZeroPartitionCountAsUnpartitionedMode() {
        var environment = validProductionEnvironment().withProperty("app.outbox.partition-count", "0");
        assertThatCode(() -> new ApplicationStartupValidator(environment).validateProductionConfig())
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "demo", "test", "testcontainers"})
    void rejectsMixedProductionAndUnsafeProfiles(String profile) {
        var environment = validProductionEnvironment();
        environment.setActiveProfiles("prod", profile);
        assertThatThrownBy(() -> new ApplicationStartupValidator(environment).validateProductionConfig())
                .hasMessageContaining("must not be combined");
    }

    @Test
    void checksProductionWhenItIsTheDefaultProfile() {
        var environment = new MockEnvironment();
        environment.setDefaultProfiles("prod");
        assertThatThrownBy(() -> new ApplicationStartupValidator(environment).validateProductionConfig())
                .hasMessageContaining("non-default JWT secret");
    }

    @Test
    void rejectsMissingDatabasePassword() {
        var environment = new MockEnvironment().withProperty("jwt.secret", "non-default-secret");
        environment.setActiveProfiles("prod");
        assertThatThrownBy(() -> new ApplicationStartupValidator(environment).validateProductionConfig())
                .hasMessageContaining("database password");
    }
}
