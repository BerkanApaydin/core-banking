package com.bank.app.user.application.usecase;

import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.port.in.TransactionalUseCase;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.user.application.port.in.ConfirmMfaUseCase;
import com.bank.app.user.application.port.out.AuthenticationPort;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.MfaSecretPort;
import com.bank.app.user.application.port.out.RefreshTokenPort;
import com.bank.app.user.application.port.out.SaveUserPort;
import com.bank.app.user.application.service.MfaCodeVerificationService;
import com.bank.app.user.domain.User;
import com.bank.app.user.domain.exception.AuthenticationFailedException;
import com.bank.app.user.domain.exception.InvalidMfaCodeException;
import com.bank.app.user.domain.exception.MfaAlreadyEnabledException;
import com.bank.app.user.domain.exception.MfaNotEnrolledException;
import com.bank.app.user.domain.exception.TooManyFailedMfaAttemptsException;
import com.bank.app.user.domain.exception.UserNotFoundException;
import java.util.Objects;

/**
 * Completes enrollment: a correct TOTP code proves the user holds the secret
 * in their authenticator, and only then is MFA switched on for logins.
 *
 * <p>The code check runs through {@link MfaCodeVerificationService}, which
 * owns the brute-force bound, the TOTP replay protection and the uniform
 * failure contract.
 */
@TransactionalUseCase
public class ConfirmMfaUseCaseImpl implements ConfirmMfaUseCase {

    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final AuthenticationPort authenticationPort;
    private final RefreshTokenPort refreshTokenPort;
    private final MfaSecretPort mfaSecretPort;
    private final MfaCodeVerificationService mfaCodeVerification;
    private final ClockProviderPort clockProvider;
    private final AuditEventPort auditEventPort;

    public ConfirmMfaUseCaseImpl(LoadUserPort loadUserPort, SaveUserPort saveUserPort,
            AuthenticationPort authenticationPort, RefreshTokenPort refreshTokenPort,
            MfaSecretPort mfaSecretPort, MfaCodeVerificationService mfaCodeVerification,
            ClockProviderPort clockProvider, AuditEventPort auditEventPort) {
        this.loadUserPort = loadUserPort;
        this.saveUserPort = saveUserPort;
        this.authenticationPort = authenticationPort;
        this.refreshTokenPort = refreshTokenPort;
        this.mfaSecretPort = mfaSecretPort;
        this.mfaCodeVerification = mfaCodeVerification;
        this.clockProvider = clockProvider;
        this.auditEventPort = auditEventPort;
    }

    @Override
    public void execute(Long userId, String password, String code) {
        Objects.requireNonNull(userId, "User ID must not be null");
        User user = loadUserPort.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        if (user.isMfaEnabled()) {
            throw new MfaAlreadyEnabledException();
        }
        // Step-up before enabling a login factor: a stolen session alone must
        // not be able to turn on an attacker-controlled second factor.
        try {
            authenticationPort.authenticate(user.getUsername(), password);
        } catch (AuthenticationFailedException wrongPassword) {
            throw new InvalidMfaCodeException();
        }
        if (mfaSecretPort.findSecret(userId).isEmpty()) {
            throw new MfaNotEnrolledException();
        }
        // Brute-force bound first: a locked-out account must not even reach
        // the code check.
        if (mfaCodeVerification.isBlocked(userId)) {
            throw new TooManyFailedMfaAttemptsException(
                    "Too many failed authenticator code attempts. Please try again in "
                            + mfaCodeVerification.getWindowMinutes() + " minutes.");
        }
        // 400, not the generic 401: this endpoint runs in an already
        // authenticated session (see InvalidMfaCodeException). The service
        // collapses "wrong code", "replayed code" and "missing secret" into
        // one rejection so the response is not an oracle.
        // The code is verified but NOT consumed: the confirmation does not
        // mint a session, and consuming the step counter here would reject
        // the very next legitimate use of the same code (confirm, then log
        // in, inside one 30-second step).
        if (!mfaCodeVerification.verify(userId, code)) {
            throw new InvalidMfaCodeException();
        }
        user.enableMfa();
        saveUserPort.save(user);
        // enableMfa() bumped tokenVersion; retire the refresh sessions too so
        // an old session cannot outlive the authentication-policy change.
        refreshTokenPort.revokeAllForUser(userId);
        EnableMfaUseCaseImpl.audit(auditEventPort, AuditEvent.MFA_ENABLED, user,
                "MFA enrollment confirmed and enabled.", clockProvider);
    }
}
