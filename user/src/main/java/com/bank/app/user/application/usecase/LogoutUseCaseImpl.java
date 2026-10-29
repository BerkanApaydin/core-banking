package com.bank.app.user.application.usecase;


import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.domain.TokenDigest;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.user.application.port.in.LogoutUseCase;
import com.bank.app.user.application.port.out.RefreshTokenPort;
import com.bank.app.user.application.port.out.RefreshTokenPort.StoredRefresh;
import com.bank.app.user.application.port.out.TokenBlacklistPort;
import com.bank.app.user.application.port.out.JwtPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

public class LogoutUseCaseImpl implements LogoutUseCase {

    private static final Logger log = LoggerFactory.getLogger(LogoutUseCaseImpl.class);

    private final TokenBlacklistPort tokenBlacklistPort;
    private final JwtPort jwtPort;
    private final RefreshTokenPort refreshTokenPort;
    private final ClockProviderPort clockProvider;
    private final AuditEventPort auditEventPort;

    public LogoutUseCaseImpl(TokenBlacklistPort tokenBlacklistPort, JwtPort jwtPort,
                             RefreshTokenPort refreshTokenPort,
                             ClockProviderPort clockProvider, AuditEventPort auditEventPort) {
        this.tokenBlacklistPort = tokenBlacklistPort;
        this.jwtPort = jwtPort;
        this.refreshTokenPort = refreshTokenPort;
        this.clockProvider = clockProvider;
        this.auditEventPort = auditEventPort;
    }

    @Override
    public void execute(String authHeader) {
        execute(authHeader, null);
    }

    @Override
    public void execute(String authHeader, String refreshToken) {
        String username = "system";
        String accessToken = null;
        JwtPort.VerifiedToken accessIdentity = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
            // M-4: already-revoked access tokens short-circuit to a pure no-op
            // (still 204 at the controller). Without this, a revoked token can
            // re-trigger server-side logout until its natural expiry — audit
            // spam plus a revocation oracle (logout 204 vs any other route
            // 401 tells whether the token was revoked). The revocation check
            // is best-effort: if the store is unreadable, fall through and
            // attempt the normal revocation path instead of dropping logout.
            if (isAlreadyRevoked(accessToken)) {
                // Access already gone but a refresh session was also presented:
                // revoke its family when the access subject is trustworthy,
                // otherwise revoke only the presented row. Then return without
                // re-auditing.
                if (hasRefreshToken(refreshToken)) {
                    Long accessUserId = verifiedUserId(accessToken);
                    if (accessUserId == null) {
                        revokeSingleRefreshToken(refreshToken);
                    } else {
                        revokeRefreshFamily(refreshToken, accessUserId);
                    }
                }
                return;
            }
            accessIdentity = verifyBestEffort(accessToken);
            username = bestEffortUsername(accessToken, refreshToken);
        } else if (hasRefreshToken(refreshToken)) {
            username = bestEffortUsername(refreshToken, null);
        }
        Long accessUserId = accessIdentity == null ? null : accessIdentity.userId();
        Long actorUserId = accessUserId;
        // R-U11: revoke the long-lived refresh family BEFORE blacklisting the
        // short-lived access token. If the database is unavailable, logout
        // fails here and the caller can retry; reversing the order would leave
        // a usable refresh token behind after the access token was already
        // burned. Rotating issues a new token in the same family, so a
        // single-row revoke would leave the replacement usable too.
        if (hasRefreshToken(refreshToken)) {
            if (actorUserId == null) {
                actorUserId = verifiedUserId(refreshToken);
            }
            if (accessToken != null && accessUserId == null) {
                // The access token was presented but could not be verified
                // (expired/forged): without a trusted subject, revoke only the
                // presented refresh row instead of a whole family.
                revokeSingleRefreshToken(refreshToken);
            } else {
                revokeRefreshFamily(refreshToken, accessUserId);
            }
        }
        if (accessToken != null) {
            long remainingMs = jwtPort.getRemainingMs(accessToken);
            if (remainingMs > 0) {
                tokenBlacklistPort.blacklist(accessToken, remainingMs);
            }
        }
        auditLogout(username, actorUserId);
    }

    private static boolean hasRefreshToken(String refreshToken) {
        return refreshToken != null && !refreshToken.isBlank();
    }

    /**
     * Logout ends the browser session, not just the one token presented:
     * revoking the family also retires the rotated successor. Unknown hashes
     * keep the idempotent single-row no-op path.
     *
     * <p>When the access token was verified, the refresh session must belong
     * to the same user. Otherwise a caller holding any valid access token
     * could terminate another user's whole refresh family (DoS) by presenting
     * a stolen refresh token.
     */
    private void revokeRefreshFamily(String refreshToken, Long expectedUserId) {
        String hash = TokenDigest.sha256Hex(refreshToken);
        StoredRefresh stored = refreshTokenPort.findByTokenHash(hash).orElse(null);
        if (stored != null) {
            if (expectedUserId != null && !expectedUserId.equals(stored.userId())) {
                log.warn("Logout refresh token subject mismatch; refresh family not revoked. "
                                + "accessUserId={}, refreshUserId={}",
                        expectedUserId, stored.userId());
                return;
            }
            refreshTokenPort.revokeFamily(stored.familyId(), stored.userId());
            return;
        }
        revokeSingleRefreshToken(refreshToken);
    }

    private void revokeSingleRefreshToken(String refreshToken) {
        refreshTokenPort.revoke(TokenDigest.sha256Hex(refreshToken));
    }

    private Long verifiedUserId(String token) {
        JwtPort.VerifiedToken identity = verifyBestEffort(token);
        return identity == null ? null : identity.userId();
    }

    private JwtPort.VerifiedToken verifyBestEffort(String token) {
        try {
            return jwtPort.verifyAndDecode(token);
        } catch (RuntimeException invalid) {
            log.debug("Logout token identity unavailable: {}",
                    invalid.getClass().getSimpleName());
            return null;
        }
    }

    private boolean isAlreadyRevoked(String token) {
        try {
            return tokenBlacklistPort.isBlacklisted(token);
        } catch (RuntimeException storeUnavailable) {
            log.debug("Logout revocation check unavailable: {}",
                    storeUnavailable.getClass().getSimpleName());
            return false;
        }
    }

    private String bestEffortUsername(String... tokens) {
        // Logout audit attribution only: an unverifiable token means the
        // caller was already anonymous — "system", never an exception.
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            try {
                String username = jwtPort.extractUsername(token);
                if (username != null && !username.isBlank()) {
                    return username;
                }
            } catch (RuntimeException invalid) {
                log.debug("Logout token not attributable: {}", invalid.getClass().getSimpleName());
            }
        }
        return "system";
    }

    /**
     * Session-termination audit (K11/D4). Best-effort like login: revocation
     * already happened above, so an audit-store outage must not turn logout
     * into a 500 — the client already dropped its cookies. The production
     * adapter queues a durable retry when the synchronous write fails (F10).
     */
    private void auditLogout(String username, Long actorUserId) {
        try {
            auditEventPort.publishBestEffort(new AuditEvent(AuditEvent.LOGOUT,
                    "User logged out; tokens revoked.",
                    LocalDateTime.now(clockProvider.clock()), username, actorUserId));
        } catch (Exception auditFailure) {
            log.warn("Logout audit write failed: failureType={}",
                    auditFailure.getClass().getSimpleName());
        }
    }
}
