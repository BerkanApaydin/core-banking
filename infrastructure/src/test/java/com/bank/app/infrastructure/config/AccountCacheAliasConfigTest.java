package com.bank.app.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The resolved bean — not just {@link CacheBackendResolution#resolve} — is what
 * adapters inject. These tests pin the value actually published to the context.
 */
class AccountCacheAliasConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AccountCacheAliasConfig.class);

    @Test
    void canonicalValuesWinOverConflictingLegacyValues() {
        runner.withPropertyValues(
                        "app.cache.account-info.backend=caffeine",
                        "app.cache.account-info.maximum-size=1111",
                        "app.cache.account-info.expire-after-write=11",
                        "app.cache.account-info.eviction-batch-size=111",
                        "app.cache.caffeine.account-info.backend=redis",
                        "app.cache.caffeine.account-info.maximum-size=9999",
                        "app.cache.caffeine.account-info.expire-after-write=99",
                        "app.cache.caffeine.account-info.eviction-batch-size=999")
                .run(context -> {
                    CacheProperties.AccountInfoCache resolved = resolved(context);

                    // An inversion here would run production on the single-JVM
                    // backend with the legacy maximum-size instead of the
                    // shared Redis region.
                    assertThat(resolved.backend()).isEqualTo("caffeine");
                    assertThat(resolved.maximumSize()).isEqualTo(1111);
                    assertThat(resolved.expireAfterWrite()).isEqualTo(11);
                    assertThat(resolved.evictionBatchSize()).isEqualTo(111);
                });
    }

    @Test
    void legacyValuesApplyWhenCanonicalKeysAreUnset() {
        runner.withPropertyValues(
                        "app.cache.caffeine.account-info.backend=redis",
                        "app.cache.caffeine.account-info.maximum-size=5000",
                        "app.cache.caffeine.account-info.expire-after-write=120",
                        "app.cache.caffeine.account-info.eviction-batch-size=250")
                .run(context -> {
                    CacheProperties.AccountInfoCache resolved = resolved(context);

                    assertThat(resolved.backend()).isEqualTo("redis");
                    assertThat(resolved.maximumSize()).isEqualTo(5000);
                    assertThat(resolved.expireAfterWrite()).isEqualTo(120);
                    assertThat(resolved.evictionBatchSize()).isEqualTo(250);
                });
    }

    private static CacheProperties.AccountInfoCache resolved(
            org.springframework.context.ApplicationContext context) {
        return context.getBean("resolvedAccountInfoCache", CacheProperties.AccountInfoCache.class);
    }
}
