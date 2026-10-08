package com.bank.app.user.application.usecase;

import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.domain.TokenDigest;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.out.AuthenticationPort;
import com.bank.app.user.application.port.out.LoginAttemptPort;
import com.bank.app.user.application.port.out.LoginAttemptStoreUnavailableException;
import com.bank.app.user.application.port.out.RefreshTokenPort;
import com.bank.app.user.domain.exception.UserNotFoundException;
import com.bank.app.user.domain.exception.AuthenticationFailedException;
import com.bank.app.user.domain.exception.TooManyFailedLoginAttemptsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
// Credential lookups use short read-only transactions in their adapters.
// The Redis-backed login guard must not run inside a relational transaction.
public class LoginUserUseCaseImpl implements LoginUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(LoginUserUseCaseImpl.class);

    private final AuthenticationPort authenticationPort;
    private final JwtPort jwtPort;
    private final LoginAttemptPort loginAttemptPort;
    private final RefreshTokenPort refreshTokenPort;
    private final ClockProviderPort clockProvider;
    private final AuditEventPort auditEventPort;

    public LoginUserUseCaseImpl(AuthenticationPort authenticationPort, JwtPort jwtPort,
                                LoginAttemptPort loginAttemptPort, RefreshTokenPort refreshTokenPort,
                                ClockProviderPort clockProvider, AuditEventPort auditEventPort) {
        this.authenticationPort = authenticationPort;
        this.jwtPort = jwtPort;
        this.loginAttemptPort = loginAttemptPort;
        this.refreshTokenPort = refreshTokenPort;
        this.clockProvider = clockProvider;
        this.auditEventPort = auditEventPort;
    }

    @Override
    public AuthResponse execute(AuthRequest request) {
        return execute(request, null);
    }

    @Override
    public AuthResponse execute(AuthRequest request, String clientIp) {
        String username = request.username();

        if (clientIp != null && loginAttemptPort.isIpBlocked(clientIp)) {
            auditLogin("LOGIN_FAILED", username, null,
                    "Blocked login attempt from IP " + clientIp + " (brute-force guard).");
            throw new TooManyFailedLoginAttemptsException(
                    "Too many failed login attempts from this IP. Please try again in "
                    + loginAttemptPort.getWindowMinutes() + " minutes.");
        }

        if (username != null && loginAttemptPort.isUsernameBlocked(username)) {
            auditLogin("LOGIN_FAILED", username, null,
                    "Blocked login attempt for username (brute-force guard).");
            throw new TooManyFailedLoginAttemptsException(
                    "Too many failed login attempts for this username. Please try again in "
                    + loginAttemptPort.getWindowMinutes() + " minutes.");
        }

        try {
            AuthenticationPort.AuthenticatedUser user = authenticationPort.authenticate(username, request.password());
            // Guard-state reset first: a failure here must not mint tokens.
            // Null-safe like the isUsernameBlocked check above: the adapters
            // treat a missing username as a no-op instead of throwing NPE.
            if (clientIp != null) loginAttemptPort.reset(clientIp);
            if (username != null) loginAttemptPort.resetByUsername(username);
            // The generation rides along with the authenticated identity (no
            // user reload: the credential check already loaded the row, and
            // the architecture bans a second load here).
            String token = jwtPort.generateToken(user.id().value(), user.username(), user.role().name(),
                    user.tokenVersion());
            String refreshToken = jwtPort.generateRefreshToken(
                    user.id().value(), user.username(), user.role().name(), user.tokenVersion());
            // Server-side session: only the digest is stored, never the token.
            // A storage failure fails the login (fail-closed): a session the
            // server cannot rotate or revoke must never be handed out.
            refreshTokenPort.save(
                    TokenDigest.sha256Hex(refreshToken),
                    user.id().value(),
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(clockProvider.clock())
                            .plus(jwtPort.getRefreshExpirationMs(), ChronoUnit.MILLIS));
            log.info("User logged in: userId={}", user.id().value());
            auditLogin("LOGIN_SUCCEEDED", user.username(), user.id().value(),
                    "User logged in successfully" + (clientIp != null ? " from IP " + clientIp : "") + ".");
            return new AuthResponse(token, refreshToken, user.id().value(), user.username(),
                    jwtPort.getExpirationMs());
        } catch (UserNotFoundException e) {
            // G9: user-enumeration guard — unknown username must be
            // indistinguishable from wrong password (401, fixed message).
            log.warn("Failed login attempt");
            if (clientIp != null) loginAttemptPort.recordFailure(clientIp, username);
            auditLogin("LOGIN_FAILED", username, null, "Failed login attempt.");
            throw new AuthenticationFailedException(e);
        } catch (AuthenticationFailedException e) {
            log.warn("Failed login attempt");
            if (clientIp != null) loginAttemptPort.recordFailure(clientIp, username);
            auditLogin("LOGIN_FAILED", username, null, "Failed login attempt.");
            throw e;
        } catch (LoginAttemptStoreUnavailableException e) {
            // A successful credential check is not enough if the login guard
            // cannot reset or record its state. Do not return the generated JWT.
            throw e;
        }
    }

    /**
     * Authentication-lifecycle audit (K11/D4). Best-effort by design — the
     * opposite of the money path: a down audit store must not lock users out
     * of login. The failure is logged so the audit gap is visible.
     */
    private void auditLogin(String action, String username, Long userId, String details) {
        try {
            auditEventPort.publish(new AuditEvent(action, details,
                    LocalDateTime.now(clockProvider.clock()),
                    username != null && !username.isBlank() ? username : "system",
                    userId));
        } catch (Exception auditFailure) {
            log.warn("Login audit write failed: action={}, failureType={}",
                    action, auditFailure.getClass().getSimpleName());
        }
    }
}
