package com.bank.app.infrastructure.adapter.in.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheBackendResolutionTest {

    @Test
    void defaultsToCaffeineWhenNothingSet() {
        CacheProperties.AccountInfoCache resolved =
                CacheBackendResolution.resolve(new MockEnvironment());

        assertEquals("caffeine", resolved.backend());
        assertFalse(CacheBackendResolution.isRedis(new MockEnvironment()));
    }

    @Test
    void legacyPrefixStillAppliesWhileCanonicalUntouched() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.cache.caffeine.account-info.backend", "redis");

        CacheProperties.AccountInfoCache resolved = CacheBackendResolution.resolve(environment);

        assertEquals("redis", resolved.backend());
        assertTrue(CacheBackendResolution.isRedis(environment));
    }

    @Test
    void shippedBaseConfigLeavesCanonicalUnsetSoLegacyOverridesApply() {
        // The base application.yml ships the CACHE_ACCOUNT_INFO_* placeholders
        // under the legacy prefix ONLY. Canonical must stay "unset" there, or
        // every deployment would resolve canonical defaults and a legacy-only
        // override would be silently ignored.
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.cache.caffeine.account-info.backend", "redis")
                .withProperty("app.cache.caffeine.account-info.maximum-size", "5000");

        CacheProperties.AccountInfoCache resolved = CacheBackendResolution.resolve(environment);

        assertEquals("redis", resolved.backend());
        assertEquals(5000, resolved.maximumSize());
    }

    @Test
    void unknownLegacyBackendStaysVisibleWhileCanonicalUnset() {
        // A typo in a legacy-only deployment must reach the startup validator
        // instead of being shadowed by canonical defaults.
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.cache.caffeine.account-info.backend", "rediss");

        assertFalse(CacheBackendResolution.isKnownBackend(environment));
    }

    @Test
    void canonicalPrefixSelectsRedisBackend() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.cache.account-info.backend", "redis");

        assertTrue(CacheBackendResolution.isRedis(environment));
        assertEquals("redis", CacheBackendResolution.resolve(environment).backend());
    }

    @Test
    void canonicalWinsOnConflict() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.cache.account-info.backend", "redis")
                .withProperty("app.cache.caffeine.account-info.backend", "caffeine");

        assertEquals("redis", CacheBackendResolution.resolve(environment).backend());
    }

    @Test
    void legacyWinsWhenCanonicalLeftAtDefaults() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.cache.caffeine.account-info.maximum-size", "5000");

        CacheProperties.AccountInfoCache resolved = CacheBackendResolution.resolve(environment);

        assertEquals(5000, resolved.maximumSize());
        assertEquals("caffeine", resolved.backend());
    }

    @Test
    void mergesPerFieldCanonicalWinsOnlyWhereCustomized() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.cache.account-info.eviction-batch-size", "250")
                .withProperty("app.cache.caffeine.account-info.backend", "redis")
                .withProperty("app.cache.caffeine.account-info.maximum-size", "5000");

        CacheProperties.AccountInfoCache resolved = CacheBackendResolution.resolve(environment);

        assertEquals(250, resolved.evictionBatchSize());
        assertEquals("redis", resolved.backend());
        assertEquals(5000, resolved.maximumSize());
    }

    @Test
    void isDefaultDetectsUntouchedRecord() {
        assertTrue(CacheProperties.AccountInfoCache.defaults().isDefault());
        assertTrue(new CacheProperties(null).accountInfo().isDefault());
        assertFalse(new CacheProperties.AccountInfoCache("redis", 1000, 60, 500).isDefault());
    }

    @Test
    void knownBackendsAreTheOnlyAcceptedValues() {
        MockEnvironment caffeine = new MockEnvironment()
                .withProperty("app.cache.account-info.backend", "caffeine");
        MockEnvironment redis = new MockEnvironment()
                .withProperty("app.cache.account-info.backend", "redis");
        MockEnvironment typo = new MockEnvironment()
                .withProperty("app.cache.account-info.backend", "rediss");

        assertTrue(CacheBackendResolution.isKnownBackend(caffeine));
        assertTrue(CacheBackendResolution.isKnownBackend(redis));
        // A typo must be visible to the startup validator, not silently
        // degraded to the single-JVM backend.
        assertFalse(CacheBackendResolution.isKnownBackend(typo));
    }
}
