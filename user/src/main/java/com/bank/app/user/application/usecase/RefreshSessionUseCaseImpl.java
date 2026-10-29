package com.bank.app.user.application.usecase;

import com.bank.app.common.application.exception.CsrfViolationException;
import com.bank.app.common.application.port.in.TransactionalUseCase;
import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.domain.TokenDigest;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.in.RefreshSessionUseCase;
import com.bank.app.user.application.port.out.CsrfBindingPort;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.RefreshTokenPort;
import com.bank.app.user.application.port.out.RefreshTokenPort.StoredRefresh;
import com.bank.app.user.domain.User;
import com.bank.app.user.domain.exception.AuthenticationFailedException;
import com.bank.app.user.domain.exception.RefreshTokenReuseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Rotating refresh-token sessions with reuse detection.
 *
 * <p>
 * Every successful call retires the presented token ({@code revoked} +
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
    private final LoadUserPort loadUserPort;
    private final ClockProviderPort clockProvider;
    private final AuditEventPort auditEventPort;
    private final CsrfBindingPort csrfBinding;

    public RefreshSessionUseCaseImpl(JwtPort jwtPort, RefreshTokenPort refreshTokenPort,
            LoadUserPort loadUserPort,
            ClockProviderPort clockProvider, AuditEventPort auditEventPort,
            CsrfBindingPort csrfBinding) {
        this.jwtPort = jwtPort;
        this.refreshTokenPort = refreshTokenPort;
        this.loadUserPort = loadUserPort;
        this.clockProvider = clockProvider;
        this.auditEventPort = auditEventPort;
        this.csrfBinding = csrfBinding;
    }

    // Audit: execute was a 79-line long method with five interleaved decisions
    // (token validity, reuse/theft, expiry, generation, rotation-race). Each
    // decision is now a named helper; execute only orchestrates. Behavior —
    // statuses, messages, audit rows — is unchanged.
    @Override
    public AuthResponse execute(String refreshToken) {
        return rotate(refreshToken, null, null);
    }

    @Override
    public AuthResponse executeWithCsrf(String refreshToken, String csrfHeader, String csrfCookie) {
        if (csrfHeader == null || csrfCookie == null) {
            throw new CsrfViolationException();
        }
        return rotate(refreshToken, csrfHeader, csrfCookie);
    }

    private AuthResponse rotate(String refreshToken, String csrfHeader, String csrfCookie) {
        JwtPort.VerifiedToken verified = verifyRefreshToken(refreshToken);
        if (csrfHeader != null
                && !csrfBinding.verifyCsrfToken(csrfHeader, csrfCookie, String.valueOf(verified.userId()))) {
            throw new CsrfViolationException();
        }
        String hash = TokenDigest.sha256Hex(refreshToken);
        StoredRefresh stored = refreshTokenPort.findByTokenHash(hash)
                .orElseThrow(() -> new AuthenticationFailedException("Invalid refresh token."));
        LocalDateTime now = LocalDateTime.now(clockProvider.clock());
        rejectIfReused(stored, verified);
        rejectIfExpired(stored, now);
        User user = loadUserForRotation(verified, refreshToken);
        return rotateTokens(stored, verified, user, hash, now);
    }

    private JwtPort.VerifiedToken verifyRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthenticationFailedException("Refresh token is required.");
        }
        JwtPort.VerifiedToken verified = jwtPort.verifyAndDecode(refreshToken);
        if (verified == null || !"refresh".equals(jwtPort.extractTokenType(refreshToken))) {
            throw new AuthenticationFailedException("Invalid refresh token.");
        }
        return verified;
    }

    private void rejectIfReused(StoredRefresh stored, JwtPort.VerifiedToken verified) {
        if (!stored.revoked()) {
            return;
        }
        if (stored.replacedByHash() != null) {
            // Reuse of a rotated token: probable theft. Kill the family.
            revokeFamilyWithAudit(stored.familyId(), stored.userId(), verified,
                    "Refresh token reuse detected; token family revoked.",
                    "Refresh token reuse detected: family revoked");
            throw new RefreshTokenReuseException();
        }
        throw new AuthenticationFailedException("Refresh token is no longer valid.");
    }

    private static void rejectIfExpired(StoredRefresh stored, LocalDateTime now) {
        if (stored.expiresAt() == null || !stored.expiresAt().isAfter(now)) {
            throw new AuthenticationFailedException("Refresh token has expired.");
        }
    }

    private User loadUserForRotation(JwtPort.VerifiedToken verified, String refreshToken) {
        // Token-generation enforcement (V39): a role or password change bumps
        // the user's generation, so a refresh minted before the bump is
        // rejected here and the client must re-authenticate. Access tokens
        // stay valid until their (short) expiry — the residual window is
        // bounded by the access lifetime, by design.
        //
        // R-T10: the row is locked for the rest of this transaction so a
        // concurrent logout/theft-revoke (which locks the same user row before
        // its family UPDATE) cannot scan the family before this rotation's
        // successor is committed.
        User user = loadUserPort.findByUsernameForUpdate(verified.username())
                .orElseThrow(() -> new AuthenticationFailedException("Invalid refresh token."));
        long tokenVersion = jwtPort.extractTokenVersion(refreshToken);
        if (tokenVersion != user.getTokenVersion()) {
            log.warn("Stale refresh token generation rejected: userId={}", verified.userId());
            // Rejection only, no state change: audit is best-effort-auth
            // (docs/decisions/audit-atomicity.md). An audit-store outage must
            // not replace the contract's 401 with a 500.
            try {
                auditEventPort.publishBestEffort(new AuditEvent(AuditEvent.TOKEN_REVOKED,
                        "Stale refresh token generation rejected; re-authentication required.",
                        LocalDateTime.now(clockProvider.clock()), verified.username(),
                        verified.userId()));
            } catch (Exception auditFailure) {
                log.warn("Stale refresh token audit write failed: userId={}, failureType={}",
                        verified.userId(), auditFailure.getClass().getSimpleName());
            }
            throw new AuthenticationFailedException("Session is no longer valid. Please log in again.");
        }
        return user;
    }

    private AuthResponse rotateTokens(StoredRefresh stored, JwtPort.VerifiedToken verified,
            User user, String hash, LocalDateTime now) {
        String newRefreshToken = jwtPort.generateRefreshToken(
                verified.userId(), verified.username(), verified.role(), user.getTokenVersion());
        String newAccessToken = jwtPort.generateToken(
                verified.userId(), verified.username(), verified.role(), user.getTokenVersion());
        String newHash = TokenDigest.sha256Hex(newRefreshToken);
        try {
            refreshTokenPort.markRotated(hash, newHash);
        } catch (IllegalArgumentException lostRace) {
            // C-1: conditional rotation lost — a concurrent refresh rotated
            // first (or the row vanished). Re-read once: an already-rotated
            // row means the other request won, which per policy is
            // indistinguishable from theft, so the same family-revoke path
            // applies. Anything else is a stale/unknown token.
            StoredRefresh current = refreshTokenPort.findByTokenHash(hash).orElse(null);
            if (current != null && current.revoked() && current.replacedByHash() != null) {
                revokeFamilyWithAudit(stored.familyId(), stored.userId(), verified,
                        "Refresh token reuse detected; token family revoked.",
                        "Refresh token reuse detected: family revoked");
                throw new RefreshTokenReuseException();
            }
            throw new AuthenticationFailedException("Invalid refresh token.");
        }
        refreshTokenPort.save(newHash, verified.userId(), stored.familyId(),
                now.plus(jwtPort.getRefreshExpirationMs(), ChronoUnit.MILLIS));
        log.info("Session refreshed: userId={}", verified.userId());
        return new AuthResponse(newAccessToken, newRefreshToken, verified.userId(),
                verified.username(), jwtPort.getExpirationMs());
    }

    private void revokeFamilyWithAudit(String familyId, Long userId,
            JwtPort.VerifiedToken verified, String auditDetails, String logMessage) {
        refreshTokenPort.revokeFamily(familyId, userId);
        // Theft response joins this transaction (K11/D4): if the audit
        // write fails, the family revocation rolls back with it — a
        // half-revoked family with no audit trail is worse than retry. The
        // RefreshTokenReuseException thrown next is a CommitOnFailure marker,
        // so the revoke + audit commit even though the caller receives 401.
        auditEventPort.publish(new AuditEvent(AuditEvent.TOKEN_REVOKED, auditDetails,
                LocalDateTime.now(clockProvider.clock()), verified.username(),
                verified.userId()));
        log.warn(logMessage);
    }
}
