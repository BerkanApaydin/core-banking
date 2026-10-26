package com.bank.app.infrastructure.config;

import org.springframework.core.env.Environment;

import java.util.Locale;
import java.util.Set;

/**
 * Canonical snapshot-cache key resolution.
 *
 * <p>Canonical prefix: {@code app.cache.account-info.*}. The historical
 * {@code app.cache.caffeine.account-info.*} prefix (see {@link CacheProperties})
 * stays bound as a deprecated fallback. Merging is per field and decided by
 * PRESENCE: when the canonical key is set — even to its default value — the
 * canonical value wins; otherwise the legacy value applies. Set a field under
 * ONE prefix per deployment and prefer the canonical one for new
 * configuration.
 *
 * <p><b>Why the shipped {@code application.yml} defines only the legacy
 * prefix:</b> presence semantics only work if "unset" is reachable. The base
 * file therefore keeps the {@code CACHE_ACCOUNT_INFO_*} placeholders under
 * the alias and leaves the canonical keys unset, so an explicitly set
 * canonical key (the literal {@code redis} in {@code application-prod.yml},
 * or any operator override) always wins, while a legacy-only deployment
 * still resolves its value — and an unknown legacy value stays visible to
 * {@code ApplicationStartupValidator} instead of being shadowed. Defining
 * the canonical keys in the base file as well would make every deployment
 * "canonical" and silently kill the fallback.
 *
 * <p>Single source of truth for backend selection: the Spring
 * {@code @Conditional}s, the resolved settings bean
 * ({@code AccountCacheAliasConfig}) and
 * {@code ApplicationStartupValidator} all resolve through {@link #resolve}.
 */
public final class CacheBackendResolution {

    public static final String CANONICAL_PREFIX = "app.cache.account-info";
    public static final String LEGACY_PREFIX = "app.cache.caffeine.account-info";

    /**
     * The only accepted backend values. Anything else is a configuration
     * mistake and must fail at startup — a typo like {@code rediss} used to
     * select the single-JVM Caffeine backend silently, which is exactly the
     * wrong behaviour in a multi-replica deployment.
     */
    public static final Set<String> KNOWN_BACKENDS = Set.of("caffeine", "redis");

    private CacheBackendResolution() {
    }

    /**
     * Resolves the effective snapshot-cache settings with per-field
     * canonical-wins semantics. Never returns null; unset keys fall back to
     * {@link CacheProperties.AccountInfoCache} defaults.
     *
     * <p>"Canonical wins" is decided by PRESENCE, not by value: a canonical
     * field that is explicitly set to the default value (for example
     * {@code maximum-size: 1000}) still wins over the legacy prefix. A
     * value comparison would make "set to the default" indistinguishable
     * from "unset" and hand the field to the legacy prefix — the exact
     * inversion the canonical/legacy split is meant to prevent.
     */
    public static CacheProperties.AccountInfoCache resolve(Environment environment) {
        CacheProperties.AccountInfoCache canonical = read(environment, CANONICAL_PREFIX);
        CacheProperties.AccountInfoCache legacy = read(environment, LEGACY_PREFIX);
        return new CacheProperties.AccountInfoCache(
                pick(environment, "backend", canonical.backend(), legacy.backend()),
                pick(environment, "maximum-size", canonical.maximumSize(), legacy.maximumSize()),
                pick(environment, "expire-after-write", canonical.expireAfterWrite(), legacy.expireAfterWrite()),
                pick(environment, "eviction-batch-size", canonical.evictionBatchSize(), legacy.evictionBatchSize()));
    }

    /**
     * Picks the canonical value when the canonical key is PRESENT in the
     * environment, otherwise the legacy one.
     *
     * <p>Presence is what makes the canonical prefix authoritative — the
     * value may legitimately equal the built-in default. When neither
     * prefix sets the key, the canonical read already returned the
     * built-in default, so returning it is correct.
     */
    private static <T> T pick(Environment environment, String property,
                              T canonicalValue, T legacyValue) {
        if (environment.containsProperty(CANONICAL_PREFIX + "." + property)) {
            return canonicalValue;
        }
        if (environment.containsProperty(LEGACY_PREFIX + "." + property)) {
            return legacyValue;
        }
        return canonicalValue;
    }

    public static boolean isRedis(Environment environment) {
        return "redis".equalsIgnoreCase(resolve(environment).backend());
    }

    /**
     * Whether the resolved backend value is one this build knows how to wire.
     * An unknown value must abort startup instead of falling back to a
     * single-JVM backend.
     */
    public static boolean isKnownBackend(Environment environment) {
        return KNOWN_BACKENDS.contains(resolve(environment).backend().toLowerCase(Locale.ROOT));
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
