package com.bank.app.infrastructure.config;

import com.bank.app.infrastructure.adapter.out.security.JwtTokenProvider;
import com.bank.app.infrastructure.testsupport.LogCapture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.InvocationTargetException;

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
                .withProperty("app.cache.caffeine.account-info.backend", "redis")
                // BCrypt cost and HSTS max-age have silent floors and are
                // required to be PRESENT in prod, so the valid baseline pins
                // them explicitly (a missing key is rejected too).
                .withProperty("app.security.password-encoder-strength", "12")
                .withProperty("app.security.hsts-max-age-seconds", "31536000")
                // Audit (MFA): production must pin a real secret-encryption key.
                .withProperty("app.security.mfa.encryption-key-base64",
                        "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=")
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

    // The default-secret row references the constant (not a literal copy) so
    // the next rotation updates this test automatically — and no real-looking
    // secret literal lives in test sources.
    private static final String DEFAULT_SECRET_CSV =
            "jwt.secret | " + JwtTokenProvider.DEFAULT_JWT_SECRET + " | non-default JWT secret";

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "jwt.secret | '' | non-default JWT secret",
            "jwt.secret | '   ' | non-default JWT secret",
            DEFAULT_SECRET_CSV,
            "spring.datasource.password | '' | database password",
            "spring.datasource.password | bank_password | default database password",
            "app.security.token-blacklist.backend | redis | hybrid or database",
            "app.security.token-blacklist.backend | caffeine | hybrid or database",
            "app.security.browser-session.secure | false | Secure cookies",
            "app.security.failed-login.backend | caffeine | shared Redis",
            "app.security.rate-limit.backend | caffeine | shared Redis",
            "app.cache.caffeine.account-info.backend | caffeine | shared Redis",
            "app.security.failed-login.max-attempts | 0 | positive value",
            "app.security.failed-login.window-minutes | -1 | positive value",
            "app.security.rate-limit.max-requests | 0 | positive value",
            "app.security.rate-limit.time-window-ms | -1 | positive value",
            "app.security.rate-limit.resource-max-requests | 0 | positive value",
            "app.security.rate-limit.resource-time-window-ms | -1 | positive value",
            "app.cache.caffeine.account-info.eviction-batch-size | 0 | positive value",
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

    @Test
    void acceptsCanonicalSnapshotCacheBackend() {
        var environment = validProductionEnvironment()
                .withProperty("app.cache.account-info.backend", "redis")
                .withProperty("app.cache.caffeine.account-info.backend", "caffeine");
        assertThatCode(() -> new ApplicationStartupValidator(environment).validateProductionConfig())
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNonRedisCanonicalSnapshotCacheBackend() {
        // "caffeine" is the default and therefore indistinguishable from unset
        // (legacy fallback would apply); use a non-default unknown value to
        // exercise the canonical rejection path.
        var environment = validProductionEnvironment()
                .withProperty("app.cache.account-info.backend", "inmemory");
        assertThatThrownBy(() -> new ApplicationStartupValidator(environment).validateProductionConfig())
                .hasMessageContaining("app.cache.account-info.backend");
    }

    @Test
    void rejectsUnknownSnapshotCacheBackendInEveryProfile() {
        // A typo such as "rediss" used to select the single-JVM Caffeine
        // backend silently. In a multi-replica deployment that is the one
        // failure mode nobody can see in the logs: an eviction on one replica
        // never reaches another, so account status reads stay stale for the
        // whole TTL. The value must be rejected in every profile, not only
        // in prod.
        for (String profile : new String[]{"dev", "demo", "test", "staging"}) {
            var environment = new MockEnvironment()
                    .withProperty("app.cache.account-info.backend", "rediss");
            environment.setActiveProfiles(profile);

            assertThatThrownBy(() -> new ApplicationStartupValidator(environment).validateProductionConfig())
                    .as("profile %s must reject an unknown cache backend", profile)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Unknown account snapshot cache backend");
        }
    }

    @Test
    void permitsCaffeineBackendInLocalOnlyProfiles() {
        // dev/demo/test/testcontainers are single-JVM by definition, so the
        // per-JVM Caffeine backend stays valid there.
        for (String profile : new String[]{"dev", "demo", "test", "testcontainers"}) {
            var environment = new MockEnvironment()
                    .withProperty("app.cache.account-info.backend", "caffeine");
            environment.setActiveProfiles(profile);

            assertThatCode(() -> new ApplicationStartupValidator(environment).validateProductionConfig())
                    .as("profile %s must accept the single-JVM cache backend", profile)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsZeroCanonicalEvictionBatchSize() {        var environment = validProductionEnvironment()
                .withProperty("app.cache.account-info.eviction-batch-size", "0");
        assertThatThrownBy(() -> new ApplicationStartupValidator(environment).validateProductionConfig())
                .hasMessageContaining("eviction-batch-size");
    }

    @Test
    void warnsOnlyWhenDefaultJwtSecretIsUsedOutsideProduction() {
        MockEnvironment withDefaultSecret = new MockEnvironment()
                .withProperty("app.cache.account-info.backend", "redis")
                .withProperty("app.security.token-blacklist.backend", "redis")
                .withProperty("jwt.secret", JwtTokenProvider.DEFAULT_JWT_SECRET);
        withDefaultSecret.setActiveProfiles("dev");
        try (LogCapture logs = LogCapture.attach(ApplicationStartupValidator.class)) {
            new ApplicationStartupValidator(withDefaultSecret).validateProductionConfig();
            org.assertj.core.api.Assertions.assertThat(logs.messages())
                    .anyMatch(message -> message.contains("Default JWT secret"));
        }

        MockEnvironment withCustomSecret = new MockEnvironment()
                .withProperty("app.cache.account-info.backend", "redis")
                .withProperty("app.security.token-blacklist.backend", "redis")
                .withProperty("jwt.secret", "custom-secret");
        withCustomSecret.setActiveProfiles("dev");
        try (LogCapture logs = LogCapture.attach(ApplicationStartupValidator.class)) {
            new ApplicationStartupValidator(withCustomSecret).validateProductionConfig();
            org.assertj.core.api.Assertions.assertThat(logs.messages()).isEmpty();
        }
    }

    @Test
    void rejectsZeroAuditRetentionDays() {
        var environment = validProductionEnvironment().withProperty("app.audit.retention-days", "0");
        assertThatThrownBy(() -> new ApplicationStartupValidator(environment).validateProductionConfig())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("positive value");
    }

    @Test
    void rejectsMissingMfaEncryptionKeyInProduction() {
        var environment = validProductionEnvironment()
                .withProperty("app.security.mfa.encryption-key-base64", "");
        assertThatThrownBy(() -> new ApplicationStartupValidator(environment).validateProductionConfig())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MFA_ENCRYPTION_KEY");
    }

    @Test
    void rejectsNonPositiveMfaPeriodInProduction() {
        var environment = validProductionEnvironment().withProperty("app.security.mfa.period-seconds", "0");
        assertThatThrownBy(() -> new ApplicationStartupValidator(environment).validateProductionConfig())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("positive value");
    }

    @Test
    void rejectsLocalhostCorsOriginInProduction() {
        var environment = validProductionEnvironment()
                .withProperty("app.security.cors.allowed-origins", "https://bank.example.com,http://localhost:3000");
        assertThatThrownBy(() -> new ApplicationStartupValidator(environment).validateProductionConfig())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("localhost CORS");
    }

    @Test
    void rejectsLoopbackVariantsInProduction() {
        for (String origin : new String[]{
                "http://127.0.0.1:3000", "http://[::1]:3000", "https://foo.localhost",
                "localhost", "not-a-uri-localhost-fallback"}) {
            // The last value exercises the raw fall-through in originHost (no URI host):
            // it is loopback only when it literally matches; use an explicit loopback raw value.
            String candidate = origin.equals("not-a-uri-localhost-fallback") ? "foo.localhost" : origin;
            var environment = validProductionEnvironment()
                    .withProperty("app.security.cors.allowed-origins", candidate);
            assertThatThrownBy(() -> new ApplicationStartupValidator(environment).validateProductionConfig())
                    .as("origin %s must be rejected", candidate)
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("localhost CORS");
        }
    }

    @Test
    void acceptsExplicitNonLoopbackCorsOrigins() {
        var environment = validProductionEnvironment()
                .withProperty("app.security.cors.allowed-origins",
                        "https://bank.example.com, https://my-localhost-app.com, ,https://localhost.example.com");
        assertThatCode(() -> new ApplicationStartupValidator(environment).validateProductionConfig())
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsBlankCorsOriginsAsUnconfigured() {
        var blank = validProductionEnvironment().withProperty("app.security.cors.allowed-origins", "   ");
        assertThatCode(() -> new ApplicationStartupValidator(blank).validateProductionConfig())
                .doesNotThrowAnyException();

        var emptyEntries = validProductionEnvironment()
                .withProperty("app.security.cors.allowed-origins", " , , ");
        assertThatCode(() -> new ApplicationStartupValidator(emptyEntries).validateProductionConfig())
                .doesNotThrowAnyException();
    }

    @Test
    void toleratesNullCorsOriginsAsUnconfigured() throws Exception {
        org.springframework.core.env.Environment environment =
                org.mockito.Mockito.mock(org.springframework.core.env.Environment.class);
        org.mockito.Mockito.when(environment.getProperty("app.security.cors.allowed-origins", ""))
                .thenReturn(null);
        var method = CorsOriginPolicy.class.getDeclaredMethod(
                "requireProdCors", org.springframework.core.env.Environment.class);
        method.setAccessible(true);

        assertThatCode(() -> {
            try {
                method.invoke(null, environment);
            } catch (InvocationTargetException e) {
                sneakyThrow(e.getCause());
            }
        }).doesNotThrowAnyException();
    }

    private static <T extends Throwable> void sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "localhost", "LOCALHOST", "127.0.0.1", "::1", "[::1]", "foo.localhost", ".localhost"})
    void detectsLoopbackHosts(String host) throws Exception {
        var method = CorsOriginPolicy.class.getDeclaredMethod("isLoopbackHost", String.class);
        method.setAccessible(true);

        assertThatCode(() -> {
            boolean loopback = (boolean) method.invoke(null, host);
            if (!loopback) {
                throw new AssertionError("expected loopback for " + host);
            }
        }).doesNotThrowAnyException();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "example.com", "my-localhost-app.com", "localhost.example.com", "127.0.0.2"})
    void rejectsNonLoopbackHosts(String host) throws Exception {
        var method = CorsOriginPolicy.class.getDeclaredMethod("isLoopbackHost", String.class);
        method.setAccessible(true);

        assertThatCode(() -> {
            boolean loopback = (boolean) method.invoke(null, host);
            if (loopback) {
                throw new AssertionError("unexpected loopback for " + host);
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void treatsNullHostAsNonLoopback() throws Exception {
        var method = CorsOriginPolicy.class.getDeclaredMethod("isLoopbackHost", String.class);
        method.setAccessible(true);

        assertThatCode(() -> {
            boolean loopback = (boolean) method.invoke(null, (Object) null);
            if (loopback) {
                throw new AssertionError("null host must not be loopback");
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void extractsHostLowerCasedFromValidOrigin() throws Exception {
        var method = CorsOriginPolicy.class.getDeclaredMethod("originHost", String.class);
        method.setAccessible(true);

        String host = (String) method.invoke(null, "https://Example.COM:8443/some/path");
        org.assertj.core.api.Assertions.assertThat(host).isEqualTo("example.com");
    }

    @Test
    void fallsBackToRawOriginWhenUriHasNoHost() throws Exception {
        var method = CorsOriginPolicy.class.getDeclaredMethod("originHost", String.class);
        method.setAccessible(true);

        String raw = (String) method.invoke(null, "notauri with spaces");
        org.assertj.core.api.Assertions.assertThat(raw).isEqualTo("notauri with spaces");
    }

    @Test
    void rejectsMissingBcryptStrengthInProduction() throws Exception {
        // A renamed/removed key must fail, not silently fall back to the floor.
        var method = StartupPropertyRules.class.getDeclaredMethod(
                "requireAtLeast", String.class, long.class);
        method.setAccessible(true);
        var rules = new StartupPropertyRules(new MockEnvironment());

        assertThatThrownBy(() -> {
            try {
                method.invoke(rules, "app.security.password-encoder-strength", 12L);
            } catch (InvocationTargetException e) {
                sneakyThrow(e.getCause());
            }
        }).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.security.password-encoder-strength")
                .hasMessageContaining("not set");
    }

    @Test
    void rejectsUnknownLegacySnapshotCacheBackendWhileCanonicalUnset() {
        // The shipped base configuration defines only the alias prefix, so an
        // unknown legacy value must reach the validator. Before the fix the
        // canonical block made "canonical is set" always true, shadowing the
        // typo and selecting the single-JVM backend silently.
        var environment = new MockEnvironment()
                .withProperty("app.cache.caffeine.account-info.backend", "rediss");
        environment.setActiveProfiles("staging");

        assertThatThrownBy(() -> new ApplicationStartupValidator(environment).validateProductionConfig())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown account snapshot cache backend");
    }
}
