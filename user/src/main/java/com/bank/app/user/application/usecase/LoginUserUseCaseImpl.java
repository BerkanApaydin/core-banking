package com.bank.app.user.application.usecase;

import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.domain.TokenDigest;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.out.AuthenticationBackendUnavailableException;
import com.bank.app.user.application.port.out.AuthenticationPort;
import com.bank.app.common.application.port.out.IdGeneratorPort;
import com.bank.app.user.application.port.out.LoginAttemptPort;
import com.bank.app.user.application.port.out.LoginAttemptStoreUnavailableException;
import com.bank.app.user.application.port.out.PasswordEncoderPort;
import com.bank.app.user.application.port.out.RefreshTokenPort;
import com.bank.app.user.application.service.MfaCodeVerificationService;
import com.bank.app.user.domain.exception.UserNotFoundException;
import com.bank.app.user.domain.exception.AuthenticationFailedException;
import com.bank.app.user.domain.exception.TooManyFailedLoginAttemptsException;
import com.bank.app.user.domain.exception.TooManyFailedMfaAttemptsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
// Credential lookups use short read-only transactions in their adapters.
// The Redis-backed login guard must not run inside a relational transaction.
public class LoginUserUseCaseImpl implements LoginUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(LoginUserUseCaseImpl.class);

    /**
     * SE-5: lazily-minted dummy BCrypt hash (same cost factor as the
     * configured production encoder strength) for the timing-oracle guard
     * below. Generated once per JVM via the port so the value is always a
     * valid hash for the configured encoder.
     */
    private volatile String dummyBcryptHash;

    private final AuthenticationPort authenticationPort;
    private final JwtPort jwtPort;
    private final LoginAttemptPort loginAttemptPort;
    private final RefreshTokenPort refreshTokenPort;
    private final PasswordEncoderPort passwordEncoderPort;
    private final IdGeneratorPort idGenerator;
    private final ClockProviderPort clockProvider;
    private final AuditEventPort auditEventPort;
    private final MfaCodeVerificationService mfaCodeVerification;

    public LoginUserUseCaseImpl(AuthenticationPort authenticationPort, JwtPort jwtPort,
                                LoginAttemptPort loginAttemptPort, RefreshTokenPort refreshTokenPort,
                                PasswordEncoderPort passwordEncoderPort, IdGeneratorPort idGenerator,
                                ClockProviderPort clockProvider, AuditEventPort auditEventPort,
                                MfaCodeVerificationService mfaCodeVerification) {
        this.authenticationPort = authenticationPort;
        this.jwtPort = jwtPort;
        this.loginAttemptPort = loginAttemptPort;
        this.refreshTokenPort = refreshTokenPort;
        this.passwordEncoderPort = passwordEncoderPort;
        this.idGenerator = idGenerator;
        this.clockProvider = clockProvider;
        this.auditEventPort = auditEventPort;
        this.mfaCodeVerification = mfaCodeVerification;
    }

    @Override
    public AuthResponse execute(AuthRequest request) {
        return execute(request, null);
    }

    // Audit: execute was a 72-line method (84 with the MFA hook) with four
    // interleaved concerns (brute-force guards, authentication, second
    // factor, session minting). Each is now a named helper; execute only
    // orchestrates. Behavior — messages, audit rows, guard interactions —
    // is unchanged.
    @Override
    public AuthResponse execute(AuthRequest request, String clientIp) {
        String username = request.username();

        rejectIfBlocked(username, clientIp);

        // Kept outside the try so the failure audit can attribute a bad MFA
        // code to the already-authenticated account; a failed password check
        // still leaves it null (the user was never resolved).
        AuthenticationPort.AuthenticatedUser user = null;
        try {
            user = authenticationPort.authenticate(username, request.password());
            // Audit (MFA): enrolled users must additionally present a TOTP code.
            // Missing, malformed and mismatched codes all fail with the same
            // fixed message as a bad password — the code path must not oracle
            // enrollment or secret validity. The throw below is caught by the
            // AuthenticationFailedException handler like a password mismatch,
            // so it feeds the same single brute-force record and audit row
            // (no double counting).
            if (user.mfaEnabled()) {
                // Shared account-keyed MFA budget: the same counter the
                // enrollment-confirmation and MFA-removal endpoints enforce.
                // Checked BEFORE any code work so a locked-out account cannot
                // keep guessing through the login endpoint either. 429 (not
                // the generic 401) and only after a valid password, so the
                // response reveals nothing to an unauthenticated caller.
                if (mfaCodeVerification.isBlocked(user.id().value())) {
                    auditLogin(AuditEvent.LOGIN_FAILED, username, user.id().value(),
                            "MFA temporarily locked after too many failed codes (brute-force guard).");
                    throw new TooManyFailedMfaAttemptsException(
                            "Please try again in "
                                    + mfaCodeVerification.getWindowMinutes() + " minutes.");
                }
                if (!verifySecondFactor(user, request.totpCode())) {
                    throw new AuthenticationFailedException();
                }
            }
            return issueSession(user, clientIp);
        } catch (UserNotFoundException e) {
            // G9: user-enumeration guard — unknown username must be
            // indistinguishable from wrong password (401, fixed message).
            // SE-5: burn one BCrypt comparison so this path costs the same as
            // a real password mismatch (Spring skips the hash when the user
            // row is missing, which is a ~250ms timing oracle). Best-effort:
            // a broken encoder must never turn login into a 500.
            burnDummyPasswordCompare(request.password());
            log.warn("Failed login attempt");
            if (clientIp != null) loginAttemptPort.recordFailure(clientIp, username);
            auditLogin(AuditEvent.LOGIN_FAILED, username, null, "Failed login attempt.");
            throw new AuthenticationFailedException(e);
        } catch (AuthenticationFailedException e) {
            log.warn("Failed login attempt");
            if (clientIp != null) loginAttemptPort.recordFailure(clientIp, username);
            auditLogin(AuditEvent.LOGIN_FAILED, username,
                    user != null ? user.id().value() : null, "Failed login attempt.");
            throw e;
        } catch (LoginAttemptStoreUnavailableException e) {
            // A successful credential check is not enough if the login guard
            // cannot update its state. Do not return the generated JWT.
            throw e;
        }
    }

    private void rejectIfBlocked(String username, String clientIp) {
        if (clientIp != null && loginAttemptPort.isIpBlocked(clientIp)) {
            auditLogin(AuditEvent.LOGIN_FAILED, username, null,
                    "Blocked login attempt from IP " + clientIp + " (brute-force guard).");
            throw new TooManyFailedLoginAttemptsException(
                    "Too many failed login attempts from this IP. Please try again in "
                    + loginAttemptPort.getWindowMinutes() + " minutes.");
        }

        // Account + IP budget (tight) and account-only budget (loose): the
        // pair key keeps one abusive IP from locking a victim out of their
        // own account, the account key still stops a distributed attack.
        if (username != null && loginAttemptPort.isUsernameBlocked(username, clientIp)) {
            auditLogin(AuditEvent.LOGIN_FAILED, username, null,
                    "Blocked login attempt for username (brute-force guard).");
            throw new TooManyFailedLoginAttemptsException(
                    "Too many failed login attempts for this username. Please try again in "
                    + loginAttemptPort.getWindowMinutes() + " minutes.");
        }
    }

    private AuthResponse issueSession(AuthenticationPort.AuthenticatedUser user, String clientIp) {
        // Guard-state update first: a failure here must not mint tokens.
        // Null-safe like the isUsernameBlocked check above: the adapters
        // treat a missing username/IP as a no-op instead of throwing NPE.
        // R-U13: the IP counter decays by one instead of fully resetting, so
        // a known-good login cannot launder an already-burned IP budget. The
        // authenticated account's own counters are cleared in full.
        if (clientIp != null) loginAttemptPort.recordIpSuccess(clientIp);
        if (user.username() != null) loginAttemptPort.resetByUsername(user.username());
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
                // D-3: session family ID comes from the ID port (seedable
                // in tests), not a hidden UUID call in the application.
                idGenerator.newId(),
                LocalDateTime.now(clockProvider.clock())
                        .plus(jwtPort.getRefreshExpirationMs(), ChronoUnit.MILLIS));
        log.info("User logged in: userId={}", user.id().value());
        auditLogin(AuditEvent.LOGIN_SUCCEEDED, user.username(), user.id().value(),
                "User logged in successfully" + (clientIp != null ? " from IP " + clientIp : "") + ".");
        return new AuthResponse(token, refreshToken, user.id().value(), user.username(),
                jwtPort.getExpirationMs());
    }

    /**
     * Verifies the TOTP second factor. Every rejection path returns false
     * (never throws): the caller renders the single generic message. The
     * shared {@link MfaCodeVerificationService} owns the brute-force bound,
     * the TOTP replay protection and the secret decryption — a decrypt
     * failure (wrong rotation key, tampered row) fails closed like a wrong
     * code and must never bypass the factor or leak its cause.
     */
    private boolean verifySecondFactor(AuthenticationPort.AuthenticatedUser user, String code) {
        try {
            // A missing/blank code is still a failed second-factor attempt:
            // delegate it so it feeds the same account-keyed MFA budget as a
            // wrong or replayed code. Rejecting it locally would let a client
            // probe the login endpoint without ever exhausting that budget.
            return mfaCodeVerification.verifyAndConsume(user.id().value(), code);
        } catch (AuthenticationBackendUnavailableException unavailable) {
            // Secret-store outage is infrastructure (503), not a wrong code:
            // propagate so it is NOT counted as a brute-force failure and the
            // caller does not render a misleading 401.
            throw unavailable;
        } catch (LoginAttemptStoreUnavailableException unavailable) {
            // The shared MFA attempt counter runs on the same store as the
            // login guard (Redis in production). An outage is infrastructure
            // (503), not a wrong code: converting it to the generic 401 here
            // would both misreport the cause and feed the failure counters
            // a state they cannot record. The caller's catch clause rethrows.
            throw unavailable;
        } catch (RuntimeException e) {
            log.warn("MFA verification unavailable: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * SE-5 timing-oracle guard: one dummy BCrypt comparison against a cached
     * dummy hash (same cost factor as production hashes).
     */
    private void burnDummyPasswordCompare(String rawPassword) {
        try {
            String hash = dummyBcryptHash;
            if (hash == null) {
                synchronized (this) {
                    hash = dummyBcryptHash;
                    if (hash == null) {
                        hash = passwordEncoderPort.encode("login-timing-oracle-dummy");
                        dummyBcryptHash = hash;
                    }
                }
            }
            passwordEncoderPort.matches(rawPassword == null ? "" : rawPassword, hash);
        } catch (RuntimeException ignored) {
            // Best-effort only: never fail a login on oracle-guard noise.
            log.debug("Dummy password comparison unavailable: {}",
                    ignored.getClass().getSimpleName());
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
