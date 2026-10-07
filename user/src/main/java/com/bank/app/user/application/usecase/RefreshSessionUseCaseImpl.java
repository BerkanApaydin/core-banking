package com.bank.app.user.application.usecase;

import com.bank.app.common.application.port.in.TransactionalUseCase;
import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.domain.TokenDigest;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.in.RefreshSessionUseCase;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.port.out.RefreshTokenPort;
import com.bank.app.user.application.port.out.RefreshTokenPort.StoredRefresh;
import com.bank.app.user.domain.exception.AuthenticationFailedException;
import com.bank.app.user.domain.exception.RefreshTokenReuseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Rotating refresh-token sessions with reuse detection.
 *
 * <p>Every successful call retires the presented token ({@code revoked} +
 * {@code replaced_by}) and issues a fresh pair in the same family. Presenting
 * an already-rotated token means a second party holds a copy: the whole
 * family is revoked (theft response) and the client must re-authenticate.
 * Clients MUST coalesce concurrent refresh calls — a legitimate double submit
 * is indistinguishable from theft.
 */
@TransactionalUseCase
public class RefreshSessionUseCaseImpl implements RefreshSessionUseCase {

    private static final Logger log = LoggerFactory.getLogger(RefreshSessionUseCaseImpl.class);

    private final JwtPort jwtPort;
    private final RefreshTokenPort refreshTokenPort;
    private final ClockProviderPort clockProvider;
    private final AuditEventPort auditEventPort;

    public RefreshSessionUseCaseImpl(JwtPort jwtPort, RefreshTokenPort refreshTokenPort,
                                     ClockProviderPort clockProvider, AuditEventPort auditEventPort) {
        this.jwtPort = jwtPort;
        this.refreshTokenPort = refreshTokenPort;
        this.clockProvider = clockProvider;
        this.auditEventPort = auditEventPort;
    }

    @Override
    public AuthResponse execute(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthenticationFailedException("Refresh token is required.");
        }
        JwtPort.VerifiedToken verified = jwtPort.verifyAndDecode(refreshToken);
        if (verified == null || !"refresh".equals(jwtPort.extractTokenType(refreshToken))) {
            throw new AuthenticationFailedException("Invalid refresh token.");
        }
        String hash = TokenDigest.sha256Hex(refreshToken);
        StoredRefresh stored = refreshTokenPort.findByTokenHash(hash)
                .orElseThrow(() -> new AuthenticationFailedException("Invalid refresh token."));
        LocalDateTime now = LocalDateTime.now(clockProvider.clock());
        if (stored.revoked()) {
            if (stored.replacedByHash() != null) {
                // Reuse of a rotated token: probable theft. Kill the family.
                refreshTokenPort.revokeFamily(stored.familyId());
                // Theft response joins this transaction (K11/D4): if the audit
                // write fails, the family revocation rolls back with it — a
                // half-revoked family with no audit trail is worse than retry.
                auditEventPort.publish(new AuditEvent("TOKEN_REVOKED",
                        "Refresh token reuse detected; token family revoked.",
                        LocalDateTime.now(clockProvider.clock()), verified.username(),
                        verified.userId()));
                log.warn("Refresh token reuse detected: family revoked");
                throw new RefreshTokenReuseException();
            }
            throw new AuthenticationFailedException("Refresh token is no longer valid.");
        }
        if (stored.expiresAt() == null || !stored.expiresAt().isAfter(now)) {
            throw new AuthenticationFailedException("Refresh token has expired.");
        }
        String newRefreshToken = jwtPort.generateRefreshToken(
                verified.userId(), verified.username(), verified.role());
        String newAccessToken = jwtPort.generateToken(
                verified.userId(), verified.username(), verified.role());
        String newHash = TokenDigest.sha256Hex(newRefreshToken);
        refreshTokenPort.markRotated(hash, newHash);
        refreshTokenPort.save(newHash, verified.userId(), stored.familyId(),
                LocalDateTime.now(clockProvider.clock())
                        .plus(jwtPort.getRefreshExpirationMs(), ChronoUnit.MILLIS));
        log.info("Session refreshed: userId={}", verified.userId());
        return new AuthResponse(newAccessToken, newRefreshToken, verified.userId(),
                verified.username(), jwtPort.getExpirationMs());
    }
}
