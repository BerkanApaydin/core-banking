package com.bank.app.infrastructure.adapter.in.config;

import com.bank.app.infrastructure.testsupport.LogCapture;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BackendSelectionValidatorTest {

    @Test
    void identifiesLocalOnlyProfiles() throws Exception {
        Method method = BackendSelectionValidator.class.getDeclaredMethod("isLocalOnlyProfile");
        method.setAccessible(true);

        for (String profile : new String[]{"dev", "demo", "test", "testcontainers"}) {
            MockEnvironment environment = new MockEnvironment();
            environment.setActiveProfiles(profile);
            assertThat((boolean) method.invoke(new BackendSelectionValidator(environment)))
                    .as(profile)
                    .isTrue();
        }

        MockEnvironment staging = new MockEnvironment();
        staging.setActiveProfiles("staging");
        assertThat((boolean) method.invoke(new BackendSelectionValidator(staging))).isFalse();
    }

    @Test
    void validateAllChecksTokenBlacklistBackendToo() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.cache.account-info.backend", "redis")
                .withProperty("app.security.token-blacklist.backend", "bogus");
        environment.setActiveProfiles("staging");

        assertThatThrownBy(() -> new BackendSelectionValidator(environment).validateAll())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown token blacklist backend");
    }

    @Test
    void blankTokenBackendFallsBackToCaffeine() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.cache.account-info.backend", "redis")
                .withProperty("app.security.token-blacklist.backend", "   ");
        environment.setActiveProfiles("dev");

        assertThatCode(() -> new BackendSelectionValidator(environment).validateAll())
                .doesNotThrowAnyException();
    }

    @Test
    void nullTokenBackendFallsBackToCaffeine() {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("app.cache.account-info.backend")).thenReturn("redis");
        when(environment.getProperty("app.security.token-blacklist.backend", "caffeine"))
                .thenReturn(null);
        when(environment.acceptsProfiles(org.mockito.ArgumentMatchers.any(
                org.springframework.core.env.Profiles.class))).thenReturn(true);

        assertThatCode(() -> new BackendSelectionValidator(environment).validateAll())
                .doesNotThrowAnyException();
    }

    @Test
    void warnsForPerJvmCaffeineBackendsOnSharedProfiles() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.cache.account-info.backend", "caffeine")
                .withProperty("app.security.token-blacklist.backend", "caffeine");
        environment.setActiveProfiles("staging");

        try (LogCapture logs = LogCapture.attach(BackendSelectionValidator.class)) {
            new BackendSelectionValidator(environment).validateAll();

            assertThat(logs.messages())
                    .anyMatch(message -> message.contains("Account snapshot cache backend"))
                    .anyMatch(message -> message.contains("Token blacklist backend"));
        }
    }

    @Test
    void doesNotWarnForSharedBackends() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.cache.account-info.backend", "redis")
                .withProperty("app.security.token-blacklist.backend", "database");
        environment.setActiveProfiles("staging");

        try (LogCapture logs = LogCapture.attach(BackendSelectionValidator.class)) {
            new BackendSelectionValidator(environment).validateAll();
            assertThat(logs.messages()).isEmpty();
        }
    }

    @Test
    void doesNotWarnForRedisOrHybridTokenBackends() {
        for (String backend : new String[]{"redis", "hybrid"}) {
            MockEnvironment environment = new MockEnvironment()
                    .withProperty("app.cache.account-info.backend", "redis")
                    .withProperty("app.security.token-blacklist.backend", backend);
            environment.setActiveProfiles("staging");

            try (LogCapture logs = LogCapture.attach(BackendSelectionValidator.class)) {
                new BackendSelectionValidator(environment).validateAll();
                assertThat(logs.messages()).as(backend).isEmpty();
            }
        }
    }

    @Test
    void doesNotWarnForPerJvmBackendsOnLocalProfiles() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.cache.account-info.backend", "caffeine")
                .withProperty("app.security.token-blacklist.backend", "caffeine");
        environment.setActiveProfiles("dev");

        try (LogCapture logs = LogCapture.attach(BackendSelectionValidator.class)) {
            new BackendSelectionValidator(environment).validateAll();
            assertThat(logs.messages()).isEmpty();
        }
    }

    @Test
    void warnsForRemoteCacheOnSharedProfileEvenWithDurableTokenBackend() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.cache.account-info.backend", "caffeine")
                .withProperty("app.security.token-blacklist.backend", "redis");
        environment.setActiveProfiles("staging");

        try (LogCapture logs = LogCapture.attach(BackendSelectionValidator.class)) {
            new BackendSelectionValidator(environment).validateAll();
            assertThat(logs.messages())
                    .anyMatch(message -> message.contains("Account snapshot cache backend"))
                    .noneMatch(message -> message.contains("Token blacklist backend"));
        }
    }
}
