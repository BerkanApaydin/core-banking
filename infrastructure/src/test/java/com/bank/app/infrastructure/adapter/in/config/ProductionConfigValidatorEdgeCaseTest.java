package com.bank.app.infrastructure.adapter.in.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Startup-safety branches that decide whether a misconfigured production
 * deployment refuses to boot.
 *
 * <p>Each case here is a silent-failure class: a malformed MFA key only breaks
 * logins after a restart, a weakened BCrypt/HSTS floor is invisible at run
 * time, and a rotation-key typo only surfaces when the affected user logs in.
 * The validator is the only place that can turn those into a fail-fast
 * startup error, so every rejection branch gets an explicit assertion.
 */
class ProductionConfigValidatorEdgeCaseTest {

    private static final String VALID_32_BYTE_KEY =
            Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes());

    private MockEnvironment validProd() {
        return new MockEnvironment()
                .withProperty("jwt.secret", "non-default-secret")
                .withProperty("spring.datasource.password", "non-default-password")
                .withProperty("app.security.token-blacklist.backend", "hybrid")
                .withProperty("app.security.browser-session.secure", "true")
                .withProperty("app.security.failed-login.backend", "redis")
                .withProperty("app.security.rate-limit.backend", "redis")
                .withProperty("app.cache.caffeine.account-info.backend", "redis")
                .withProperty("app.security.password-encoder-strength", "12")
                .withProperty("app.security.hsts-max-age-seconds", "31536000")
                .withProperty("app.security.mfa.encryption-key-base64", VALID_32_BYTE_KEY)
                .withProperty("spring.profiles.active", "prod");
    }

    private void assertValid(MockEnvironment environment) {
        assertThatCode(() -> new ProductionConfigValidator(environment).validate())
                .doesNotThrowAnyException();
    }

    private void assertRejected(MockEnvironment environment, String messageFragment) {
        assertThatThrownBy(() -> new ProductionConfigValidator(environment).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(messageFragment);
    }

    @Test
    void acceptsBaselineProductionConfiguration() {
        assertValid(validProd());
    }

    @Test
    void rejectsMfaKeyThatIsNotBase64() {
        assertRejected(validProd().withProperty(
                "app.security.mfa.encryption-key-base64", "not-base64!!!"),
                "not valid Base64");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsBlankMfaKey(String key) {
        assertRejected(validProd().withProperty("app.security.mfa.encryption-key-base64", key),
                "MFA_ENCRYPTION_KEY");
    }

    @Test
    void rejectsMfaKeyWithWrongDecodedLength() {
        String sixteenBytes = Base64.getEncoder().encodeToString(new byte[16]);

        assertRejected(validProd().withProperty("app.security.mfa.encryption-key-base64", sixteenBytes),
                "must decode to 32 bytes");
    }

    @Test
    void stripsWhitespaceAroundMfaKeyBeforeDecoding() {
        assertValid(validProd().withProperty(
                "app.security.mfa.encryption-key-base64", "  " + VALID_32_BYTE_KEY + "  "));
    }

    @Test
    void acceptsEmptyPreviousKeyRing() {
        assertValid(validProd().withProperty(
                "app.security.mfa.previous-encryption-keys-base64", ""));
        assertValid(validProd().withProperty(
                "app.security.mfa.previous-encryption-keys-base64", "   "));
    }

    @Test
    void acceptsPreviousKeyRingWithBlankEntries() {
        assertValid(validProd().withProperty(
                "app.security.mfa.previous-encryption-keys-base64",
                " , " + VALID_32_BYTE_KEY + " , "));
    }

    @Test
    void rejectsMalformedPreviousKey() {
        assertRejected(validProd().withProperty(
                "app.security.mfa.previous-encryption-keys-base64",
                VALID_32_BYTE_KEY + ",not-base64!!!"),
                "previous encryption key is not valid Base64");
    }

    @Test
    void rejectsPreviousKeyWithWrongDecodedLength() {
        String sixteenBytes = Base64.getEncoder().encodeToString(new byte[16]);

        assertRejected(validProd().withProperty(
                "app.security.mfa.previous-encryption-keys-base64", sixteenBytes),
                "previous encryption key must decode to 32 bytes");
    }

    @Test
    void rejectsBcryptStrengthBelowFloor() {
        assertRejected(validProd().withProperty("app.security.password-encoder-strength", "11"),
                ">= 12 (found: 11)");
    }

    @Test
    void rejectsHstsMaxAgeBelowOneYear() {
        assertRejected(validProd().withProperty("app.security.hsts-max-age-seconds", "31535999"),
                ">= 31536000 (found: 31535999)");
    }

    @Test
    void treatsAbsentFloorPropertyAsMissingRatherThanUsingTheFloor() {
        StartupPropertyRules rules = new StartupPropertyRules(new MockEnvironment());

        assertThatThrownBy(() -> rules.requireAtLeast("app.security.password-encoder-strength", 12))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not set");
    }

    @Test
    void acceptsFloorValueEqualToTheMinimum() {
        StartupPropertyRules rules = new StartupPropertyRules(new MockEnvironment()
                .withProperty("app.security.password-encoder-strength", "12"));

        assertThatCode(() -> rules.requireAtLeast("app.security.password-encoder-strength", 12))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsZeroMfaMaxAttempts() {
        assertRejected(validProd().withProperty("app.security.mfa.max-attempts", "0"),
                "app.security.mfa.max-attempts");
    }

    @Test
    void rejectsNegativeMfaWindowMinutes() {
        assertRejected(validProd().withProperty("app.security.mfa.window-minutes", "-1"),
                "app.security.mfa.window-minutes");
    }

    @Test
    void rejectsZeroGlobalFailedLoginBudget() {
        assertRejected(validProd().withProperty(
                "app.security.failed-login.global-account-max-attempts", "0"),
                "global-account-max-attempts");
    }

    @Test
    void rejectsNonPositiveMfaResetTokenValidity() {
        assertRejected(validProd().withProperty(
                "app.security.mfa.reset-token-validity-minutes", "0"),
                "reset-token-validity-minutes");
    }

    @Test
    void rejectsNonPositiveMfaPeriod() {
        assertRejected(validProd().withProperty("app.security.mfa.period-seconds", "-30"),
                "period-seconds");
    }

    @ParameterizedTest
    @ValueSource(strings = {"redis", "caffeine", ""})
    void rejectsTokenRevocationBackendsThatAreNotDurable(String backend) {
        assertRejected(validProd().withProperty(
                "app.security.token-blacklist.backend", backend),
                "hybrid or database");
    }

    @Test
    void rejectsInsecureBrowserSessionCookie() {
        assertRejected(validProd().withProperty("app.security.browser-session.secure", "false"),
                "Secure cookies");
    }

    @Test
    void rejectsCanonicalSnapshotCacheBackendThatIsNotRedis() {
        assertRejected(validProd().withProperty(
                "app.cache.account-info.backend", "inmemory"),
                "app.cache.account-info.backend");
    }

    @Test
    void acceptsCanonicalRedisBackendEvenWhenLegacyAliasPointsElsewhere() {
        assertValid(validProd()
                .withProperty("app.cache.account-info.backend", "redis")
                .withProperty("app.cache.caffeine.account-info.backend", "caffeine"));
    }

    @Test
    void rejectsZeroEvictionBatchSize() {
        assertRejected(validProd().withProperty(
                "app.cache.account-info.eviction-batch-size", "0"),
                "eviction-batch-size");
    }

    @Test
    void rejectsZeroAuditRetention() {
        assertRejected(validProd().withProperty("app.audit.retention-days", "0"),
                "app.audit.retention-days");
    }

    @Test
    void rejectsZeroOutboxBatchSize() {
        assertRejected(validProd().withProperty("app.outbox.batch-size", "0"),
                "app.outbox.batch-size");
    }

    @Test
    void rejectsNegativeOutboxPartitionCount() {
        assertRejected(validProd().withProperty("app.outbox.partition-count", "-1"),
                "non-negative value");
    }

    @Test
    void acceptsZeroOutboxPartitionCount() {
        assertValid(validProd().withProperty("app.outbox.partition-count", "0"));
    }
}
