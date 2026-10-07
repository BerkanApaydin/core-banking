package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.port.out.RevocationStoreUnavailableException;
import com.bank.app.user.application.port.out.TokenBlacklistPort;
import com.bank.app.common.domain.TokenDigest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Durable, shared token revocations. Every negative lookup reads the primary
 * database; a per-pod negative cache would allow a logout on another pod to
 * remain invisible until its cache entry expires.
 */
public final class DatabaseTokenBlacklistAdapter implements TokenBlacklistPort {

    private static final String UPSERT = """
            INSERT INTO token_revocations (token_hash, expires_at) VALUES (?, ?)
            ON CONFLICT (token_hash) DO UPDATE
            SET expires_at = GREATEST(token_revocations.expires_at, EXCLUDED.expires_at)
            """;
    private static final String ACTIVE = """
            SELECT EXISTS (SELECT 1 FROM token_revocations
                           WHERE token_hash = ? AND expires_at > clock_timestamp())
            """;
    private static final String DELETE_EXPIRED_BATCH = """
            DELETE FROM token_revocations WHERE token_hash IN
                (SELECT token_hash FROM token_revocations
                 WHERE expires_at <= clock_timestamp()
                 ORDER BY expires_at LIMIT 1000)
            """;

    private final JdbcTemplate jdbc;
    private final JwtPort jwtPort;
    private final TransactionTemplate writeTransaction;

    public DatabaseTokenBlacklistAdapter(JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager, JwtPort jwtPort) {
        this.jdbc = jdbc;
        this.jwtPort = jwtPort;
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.writeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void blacklist(String token, long ignoredRemainingMs) {
        JwtPort.VerifiedToken verified = jwtPort.verifyAndDecode(token);
        if (verified == null) {
            throw new IllegalArgumentException("A valid signed token is required for revocation");
        }
        // Use the signed absolute expiry, not a pod's remaining-TTL calculation:
        // clock skew must not delete a revocation before the JWT expires.
        OffsetDateTime expiresAt = OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(verified.expiresAtMs()), ZoneOffset.UTC);
        String tokenHash = TokenDigest.sha256Hex(token);
        try {
            // execute includes commit. A proxy @Transactional catch inside the
            // adapter would miss a commit failure after the method returned.
            writeTransaction.execute(status -> {
                jdbc.update(UPSERT, tokenHash, expiresAt);
                return null;
            });
        } catch (RuntimeException e) {
            throw new RevocationStoreUnavailableException(e);
        }
    }

    @Override
    public boolean isBlacklisted(String token) {
        try {
            Boolean revoked = jdbc.queryForObject(ACTIVE, Boolean.class, TokenDigest.sha256Hex(token));
            if (revoked == null) {
                throw new IllegalStateException("Database revocation lookup returned no result");
            }
            return revoked;
        } catch (RuntimeException e) {
            throw new RevocationStoreUnavailableException(e);
        }
    }

    @Override
    public void cleanExpired() {
        // Each statement commits separately and touches at most 1,000 rows.
        // Cap one scheduled run so a large backlog does not monopolize a pod.
        for (int batch = 0; batch < 10; batch++) {
            if (jdbc.update(DELETE_EXPIRED_BATCH) < 1000) {
                break;
            }
        }
    }
}
