package com.bank.app.infrastructure.adapter.in.config;

import org.springframework.core.env.Environment;

/**
 * Canonical snapshot-cache key resolution.
 *
 * <p>Canonical prefix: {@code app.cache.account-info.*}. The historical
 * {@code app.cache.caffeine.account-info.*} prefix (see {@link CacheProperties})
 * stays bound as a deprecated fallback. Merging is per field: a canonical
 * field set to a non-default value wins, otherwise the legacy field applies.
 * A canonical field left exactly at its default is indistinguishable from
 * unset, so the legacy value applies there — set a field under ONE prefix per
 * deployment and prefer the canonical one for new configuration.
 *
 * <p>Single source of truth for backend selection: the Spring
 * {@code @Conditional}s, the resolved settings bean
 * ({@code AccountCacheAliasConfig}) and
 * {@code ApplicationStartupValidator} all resolve through {@link #resolve}.
 */
public final class CacheBackendResolution {

    public static final String CANONICAL_PREFIX = "app.cache.account-info";
    public static final String LEGACY_PREFIX = "app.cache.caffeine.account-info";

    private CacheBackendResolution() {
    }

    /**
     * Resolves the effective snapshot-cache settings with per-field
     * canonical-wins semantics. Never returns null; unset keys fall back to
     * {@link CacheProperties.AccountInfoCache} defaults.
     */
    public static CacheProperties.AccountInfoCache resolve(Environment environment) {
        CacheProperties.AccountInfoCache canonical = read(environment, CANONICAL_PREFIX);
        CacheProperties.AccountInfoCache legacy = read(environment, LEGACY_PREFIX);
        CacheProperties.AccountInfoCache defaults = CacheProperties.AccountInfoCache.defaults();
        return new CacheProperties.AccountInfoCache(
                !canonical.backend().equals(defaults.backend()) ? canonical.backend() : legacy.backend(),
                canonical.maximumSize() != defaults.maximumSize()
                        ? canonical.maximumSize() : legacy.maximumSize(),
                canonical.expireAfterWrite() != defaults.expireAfterWrite()
                        ? canonical.expireAfterWrite() : legacy.expireAfterWrite(),
                canonical.evictionBatchSize() != defaults.evictionBatchSize()
                        ? canonical.evictionBatchSize() : legacy.evictionBatchSize());
    }

    public static boolean isRedis(Environment environment) {
        return "redis".equalsIgnoreCase(resolve(environment).backend());
    }

    private static CacheProperties.AccountInfoCache read(Environment environment, String prefix) {
        String backend = environment.getProperty(prefix + ".backend", "caffeine");
        long maximumSize = longProperty(environment, prefix + ".maximum-size", 1000L);
        long expireAfterWrite = longProperty(environment, prefix + ".expire-after-write", 60L);
        long evictionBatchSize = longProperty(environment, prefix + ".eviction-batch-size", 500L);
        return new CacheProperties.AccountInfoCache(backend, maximumSize, expireAfterWrite, evictionBatchSize);
    }

    private static long longProperty(Environment environment, String key, long defaultValue) {
        Long value = environment.getProperty(key, Long.class, defaultValue);
        return value == null ? defaultValue : value;
    }
}
