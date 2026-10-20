package com.bank.app.user.application.usecase;

import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.port.in.TransactionalUseCase;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.user.application.port.in.DisableMfaUseCase;
import com.bank.app.user.application.port.out.AuthenticationBackendUnavailableException;
import com.bank.app.user.application.port.out.AuthenticationPort;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.MfaSecretEncryptorPort;
import com.bank.app.user.application.port.out.MfaSecretPort;
import com.bank.app.user.application.port.out.SaveUserPort;
import com.bank.app.user.application.service.MfaCodeVerificationService;
import com.bank.app.user.domain.User;
import com.bank.app.user.domain.exception.AuthenticationFailedException;
import com.bank.app.user.domain.exception.InvalidMfaCodeException;
import com.bank.app.user.domain.exception.MfaNotEnrolledException;
import com.bank.app.user.domain.exception.TooManyFailedMfaAttemptsException;
import com.bank.app.user.domain.exception.UserNotFoundException;
import java.util.Objects;

/**
 * Removes the second factor. Both the password (re-authentication: disabling
 * MFA must never ride on a stale session alone) and a current TOTP code
 * (possession proof) are required.
 *
 * <p>The code check runs through {@link MfaCodeVerificationService}, which
 * owns the brute-force bound and the TOTP replay protection.
 */
@TransactionalUseCase
public class DisableMfaUseCaseImpl implements DisableMfaUseCase {

    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final AuthenticationPort authenticationPort;
    private final MfaSecretPort mfaSecretPort;
    private final MfaCodeVerificationService mfaCodeVerification;
    private final ClockProviderPort clockProvider;
    private final AuditEventPort auditEventPort;

    public DisableMfaUseCaseImpl(LoadUserPort loadUserPort, SaveUserPort saveUserPort,
            AuthenticationPort authenticationPort, MfaSecretPort mfaSecretPort,
            MfaCodeVerificationService mfaCodeVerification,
            ClockProviderPort clockProvider, AuditEventPort auditEventPort) {
        this.loadUserPort = loadUserPort;
        this.saveUserPort = saveUserPort;
        this.authenticationPort = authenticationPort;
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
        if (!user.isMfaEnabled()) {
            throw new MfaNotEnrolledException();
        }
        // Re-authentication first: a wrong password fails before any TOTP work.
        // Both factors share one fixed failure (400, not 401 — see
        // InvalidMfaCodeException): no oracle on which factor failed, and no
        // session-killing 401 inside an authenticated flow. Backend outages
        // (AuthenticationBackendUnavailableException) still propagate as 503.
        try {
            authenticationPort.authenticate(user.getUsername(), password);
        } catch (AuthenticationFailedException wrongPassword) {
            throw new InvalidMfaCodeException();
        }
        if (mfaSecretPort.findSecret(userId).isEmpty()) {
            throw new MfaNotEnrolledException();
        }
        if (mfaCodeVerification.isBlocked(userId)) {
            throw new TooManyFailedMfaAttemptsException(
                    "Too many failed authenticator code attempts. Please try again in "
                            + mfaCodeVerification.getWindowMinutes() + " minutes.");
        }
        // The code is verified but NOT consumed: removing the second factor
        // does not mint a session, and consuming the step counter here would
        // reject a legitimate removal right after a login in the same
        // 30-second step.
        if (!mfaCodeVerification.verify(userId, code)) {
            throw new InvalidMfaCodeException();
        }
        user.disableMfa();
        saveUserPort.save(user);
        mfaSecretPort.deleteSecret(userId);
        EnableMfaUseCaseImpl.audit(auditEventPort, AuditEvent.MFA_DISABLED, user,
                "MFA disabled and secret deleted.", clockProvider);
    }
}
