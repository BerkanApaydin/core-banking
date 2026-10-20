package com.bank.app.infrastructure.adapter.in.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import java.util.Locale;
import java.util.Set;

/**
 * Validates backend selection in EVERY profile, not just prod.
 *
 * <p>An unknown value (a typo such as {@code rediss}) used to select the
 * single-JVM Caffeine backend silently, and a per-JVM cache in a
 * multi-replica deployment serves stale account snapshots to every replica
 * that did not perform the invalidation — with nothing in the logs to say so.
 */
final class BackendSelectionValidator {

    private static final Logger log = LoggerFactory.getLogger(BackendSelectionValidator.class);

    /**
     * The token-blacklist backends this build knows how to wire. Anything
     * else is a configuration mistake and must fail at startup.
     */
    private static final Set<String> KNOWN_BLACKLIST_BACKENDS =
            Set.of("caffeine", "redis", "database", "hybrid");

    private final Environment environment;

    BackendSelectionValidator(Environment environment) {
        this.environment = environment;
    }

    void validateAll() {
        validateSnapshotCacheBackend();
        validateTokenBlacklistBackend();
    }

    /**
     * Rejects an unknown snapshot-cache backend in every profile and warns
     * when a shared deployment would run on the per-JVM backend.
     */
    private void validateSnapshotCacheBackend() {
        CacheProperties.AccountInfoCache snapshotCache = CacheBackendResolution.resolve(environment);
        String backend = snapshotCache.backend();
        if (!CacheBackendResolution.KNOWN_BACKENDS.contains(backend.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "Unknown account snapshot cache backend '" + backend + "'. Supported values: "
                    + CacheBackendResolution.KNOWN_BACKENDS + ". Set "
                    + CacheBackendResolution.CANONICAL_PREFIX + ".backend.");
        }
        if (!"redis".equalsIgnoreCase(backend) && !isLocalOnlyProfile()) {
            log.warn("Account snapshot cache backend '{}' is per-JVM: an eviction on one replica never "
                    + "reaches another, so account status/currency reads go stale cluster-wide for up to "
                    + "the TTL. Use {}.backend=redis for any deployment with more than one replica.",
                    backend, CacheBackendResolution.CANONICAL_PREFIX);
        }
    }

    /**
     * Rejects an unknown token-blacklist backend in EVERY profile.
     *
     * <p>Only the {@code database} and {@code hybrid} backends are declared
     * in {@code TokenBlacklistBackendConfig}; the local Caffeine adapter is
     * an unconditional component. An unknown value therefore resolved to the
     * per-JVM adapter SILENTLY — and in a multi-replica deployment a logout
     * on one pod is invisible to the others until the token expires. The
     * value is now checked before any bean is created.
     */
    private void validateTokenBlacklistBackend() {
        String backend = environment.getProperty("app.security.token-blacklist.backend", "caffeine");
        if (backend == null || backend.isBlank()) {
            backend = "caffeine";
        }
        if (!KNOWN_BLACKLIST_BACKENDS.contains(backend.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "Unknown token blacklist backend '" + backend + "'. Supported values: "
                    + KNOWN_BLACKLIST_BACKENDS + ". Set app.security.token-blacklist.backend.");
        }
        if (!"redis".equalsIgnoreCase(backend) && !"database".equalsIgnoreCase(backend)
                && !"hybrid".equalsIgnoreCase(backend) && !isLocalOnlyProfile()) {
            log.warn("Token blacklist backend '{}' is per-JVM: a logout on one replica is invisible "
                    + "to the others until the token expires. Use redis/hybrid/database for any "
                    + "deployment with more than one replica.", backend);
        }
    }

    /**
     * Profiles that are single-JVM by definition (local development, tests).
     * Any other profile may run on more than one replica, so it must use the
     * shared Redis backend.
     */
    private boolean isLocalOnlyProfile() {
        return environment.acceptsProfiles(Profiles.of("dev", "demo", "test", "testcontainers"));
    }
}
