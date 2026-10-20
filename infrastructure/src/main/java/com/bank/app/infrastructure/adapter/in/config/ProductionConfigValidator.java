package com.bank.app.infrastructure.adapter.in.config;

import com.bank.app.infrastructure.adapter.out.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import java.util.Base64;

/**
 * Production-profile requirements: secrets, security floors, shared
 * backends, CORS, and retention/outbox settings.
 */
final class ProductionConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(ProductionConfigValidator.class);

    // Single-sourced from JwtTokenProvider: one literal, no silent divergence.
    private static final String DEFAULT_JWT_SECRET = JwtTokenProvider.DEFAULT_JWT_SECRET;

    private final Environment environment;
    private final StartupPropertyRules rules;

    ProductionConfigValidator(Environment environment) {
        this.environment = environment;
        this.rules = new StartupPropertyRules(environment);
    }

    void validate() {
        String jwtSecret = environment.getProperty("jwt.secret", DEFAULT_JWT_SECRET);

        if (environment.acceptsProfiles(Profiles.of("dev", "demo", "test", "testcontainers"))) {
            throw new IllegalStateException("Production profile must not be combined with development or test profiles");
        }

        String dbPassword = environment.getProperty("spring.datasource.password", "");
        if (jwtSecret == null || jwtSecret.isBlank() || DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "Production profile requires a non-default JWT secret. Set JWT_SECRET environment variable.");
        }
        if (dbPassword == null || dbPassword.isBlank()) {
            throw new IllegalStateException(
                    "Production profile requires a database password via environment variable.");
        }
        if ("bank_password".equals(dbPassword)) {
            throw new IllegalStateException(
                    "Production profile must not use the default database password. "
                    + "Set DB_PASSWORD environment variable to a secure password.");
        }
        String blacklistBackend = environment.getProperty("app.security.token-blacklist.backend", "");
        if (!"hybrid".equals(blacklistBackend) && !"database".equals(blacklistBackend)) {
            throw new IllegalStateException(
                    "Production token revocations require the hybrid or database backend");
        }
        requireSecurityFloors();
        CacheProperties.AccountInfoCache snapshotCache = requireSharedAccountCache();
        requireRateLimitsAndCors();
        requireSnapshotEvictionBounds(snapshotCache);
        requireRetentionAndOutbox();
        // Audit (MFA): TOTP secrets are AES-GCM-encrypted at rest. An ephemeral
        // fallback key (blank MFA_ENCRYPTION_KEY) makes enrolled secrets
        // unreadable after restart — acceptable for dev, never for prod.
        requireMfaEncryptionKey();
        rules.requirePositive("app.security.mfa.period-seconds", 30L);
        rules.requirePositive("app.security.mfa.reset-token-validity-minutes", 15L);
    }

    /**
     * Security floors whose weakening is silent: BCrypt cost and HSTS
     * max-age are env-overridable, so a typo (or a copy-pasted dev value)
     * could quietly weaken the password hash or turn off downgrade
     * protection. Existing hashes keep verifying after a cost change (the
     * cost is embedded in the hash), so the floor is about the cost of NEW
     * hashes.
     */
    private void requireSecurityFloors() {
        rules.requireAtLeast("app.security.password-encoder-strength", 12);
        rules.requireAtLeast("app.security.hsts-max-age-seconds", 31536000L);
        rules.requirePositive("app.security.failed-login.global-account-max-attempts", 50L);
        rules.requirePositive("app.security.mfa.max-attempts", 5L);
        rules.requirePositive("app.security.mfa.window-minutes", 15L);
        if (!environment.getProperty("app.security.browser-session.secure", Boolean.class, false)) {
            throw new IllegalStateException("Production browser sessions require Secure cookies");
        }
        requireRedisBackend("app.security.failed-login.backend");
        requireRedisBackend("app.security.rate-limit.backend");
    }

    /**
     * Snapshot cache: Caffeine/InMemory is per-JVM. With HPA running
     * multiple pods an eviction on one replica never reaches the others,
     * so account status/currency reads go stale cluster-wide up to the
     * TTL. Prod pins backend=redis (literal, see application-prod.yml);
     * fail fast here as well so no env/flag override can silently
     * reinstate the local backend in production. Resolved with
     * canonical-wins semantics: either prefix may carry the value.
     */
    private CacheProperties.AccountInfoCache requireSharedAccountCache() {
        CacheProperties.AccountInfoCache snapshotCache = CacheBackendResolution.resolve(environment);
        if (!"redis".equalsIgnoreCase(snapshotCache.backend())) {
            throw new IllegalStateException(
                    "Production requires a shared Redis backend: app.cache.account-info.backend "
                    + "(canonical; legacy app.cache.caffeine.account-info.backend still honored as fallback)");
        }
        // maximum-size is a Caffeine-only setting: the Redis backend has no
        // entry-count cap, so the region is bounded by the TTL instead
        // (memory ~= write rate x TTL). Saying so once at startup beats an
        // operator discovering it from a full broker.
        log.info("Account snapshot cache: shared Redis backend. 'maximum-size' ({}) applies to the "
                        + "Caffeine backend only — the Redis region is bounded by its TTL ({}s). Size the "
                        + "broker with maxmemory, not with this key.",
                snapshotCache.maximumSize(), snapshotCache.expireAfterWrite());
        return snapshotCache;
    }

    private void requireRateLimitsAndCors() {
        CorsOriginPolicy.requireProdCors(environment);
        rules.requirePositive("app.security.failed-login.max-attempts", 5L);
        rules.requirePositive("app.security.failed-login.window-minutes", 15L);
        rules.requirePositive("app.security.rate-limit.max-requests", 10L);
        rules.requirePositive("app.security.rate-limit.time-window-ms", 10_000L);
        rules.requirePositive("app.security.rate-limit.resource-max-requests", 120L);
        rules.requirePositive("app.security.rate-limit.resource-time-window-ms", 60_000L);
    }

    /**
     * Snapshot-cache SCAN batch: zero would make evict-all a no-op loop.
     * Resolved with canonical-wins semantics (see above).
     */
    private void requireSnapshotEvictionBounds(CacheProperties.AccountInfoCache snapshotCache) {
        if (snapshotCache.evictionBatchSize() <= 0) {
            throw new IllegalStateException(
                    "Production requires a positive value: app.cache.account-info.eviction-batch-size");
        }
    }

    /**
     * Outbox poller misconfiguration silently stops money-movement
     * callbacks: a zero batch/negative retry count must fail fast at
     * startup, not as a stalled outbox in production.
     */
    private void requireRetentionAndOutbox() {
        rules.requirePositive("app.outbox.max-retries", 5L);
        rules.requirePositive("app.outbox.batch-size", 50L);
        rules.requirePositive("app.outbox.poll-delay-ms", 2000L);
        rules.requirePositive("app.outbox.retention-days", 30L);
        rules.requirePositive("app.audit.retention-days", 365L);
        // partition-count 0 is the legal unpartitioned mode (single thread);
        // only negatives (typos) are rejected.
        rules.requireNonNegative("app.outbox.partition-count", 2L);
    }

    private void requireMfaEncryptionKey() {
        String mfaKey = environment.getProperty("app.security.mfa.encryption-key-base64", "");
        if (mfaKey == null || mfaKey.isBlank()) {
            throw new IllegalStateException(
                    "Production requires an MFA secret-encryption key: "
                    + "set MFA_ENCRYPTION_KEY (Base64, 32 bytes).");
        }
        final byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(mfaKey.strip());
        } catch (IllegalArgumentException badBase64) {
            throw new IllegalStateException(
                    "Production MFA secret-encryption key is not valid Base64: "
                    + "set MFA_ENCRYPTION_KEY to 'openssl rand -base64 32' output.");
        }
        if (decoded.length != 32) {
            throw new IllegalStateException(
                    "Production MFA secret-encryption key must decode to 32 bytes (AES-256), got "
                    + decoded.length + ". Regenerate with: openssl rand -base64 32");
        }
    }

    private void requireRedisBackend(String property) {
        if (!"redis".equals(environment.getProperty(property))) {
            throw new IllegalStateException("Production requires a shared Redis backend: " + property);
        }
    }
}
