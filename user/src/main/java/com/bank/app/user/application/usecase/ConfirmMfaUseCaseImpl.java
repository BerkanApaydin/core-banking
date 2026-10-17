package com.bank.app.user.application.usecase;

import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.port.in.TransactionalUseCase;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.user.application.port.in.ConfirmMfaUseCase;
import com.bank.app.user.application.port.out.AuthenticationBackendUnavailableException;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.MfaSecretEncryptorPort;
import com.bank.app.user.application.port.out.MfaSecretPort;
import com.bank.app.user.application.port.out.SaveUserPort;
import com.bank.app.user.domain.TotpPolicy;
import com.bank.app.user.domain.User;
import com.bank.app.user.domain.exception.InvalidMfaCodeException;
import com.bank.app.user.domain.exception.MfaAlreadyEnabledException;
import com.bank.app.user.domain.exception.MfaNotEnrolledException;
import com.bank.app.user.domain.exception.UserNotFoundException;
import java.util.Objects;

/**
 * Completes enrollment: a correct TOTP code proves the user holds the secret
 * in their authenticator, and only then is MFA switched on for logins.
 */
@TransactionalUseCase
public class ConfirmMfaUseCaseImpl implements ConfirmMfaUseCase {

    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final MfaSecretPort mfaSecretPort;
    private final MfaSecretEncryptorPort encryptor;
    private final TotpPolicy totpPolicy;
    private final ClockProviderPort clockProvider;
    private final AuditEventPort auditEventPort;

    public ConfirmMfaUseCaseImpl(LoadUserPort loadUserPort, SaveUserPort saveUserPort,
            MfaSecretPort mfaSecretPort, MfaSecretEncryptorPort encryptor, TotpPolicy totpPolicy,
            ClockProviderPort clockProvider, AuditEventPort auditEventPort) {
        this.loadUserPort = loadUserPort;
        this.saveUserPort = saveUserPort;
        this.mfaSecretPort = mfaSecretPort;
        this.encryptor = encryptor;
        this.totpPolicy = totpPolicy;
        this.clockProvider = clockProvider;
        this.auditEventPort = auditEventPort;
    }

    @Override
    public void execute(Long userId, String code) {
        Objects.requireNonNull(userId, "User ID must not be null");
        User user = loadUserPort.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        if (user.isMfaEnabled()) {
            throw new MfaAlreadyEnabledException();
        }
        String ciphertext = mfaSecretPort.findSecret(userId)
                .orElseThrow(MfaNotEnrolledException::new);
        final byte[] secret;
        try {
            secret = encryptor.decrypt(ciphertext);
        } catch (AuthenticationBackendUnavailableException unavailable) {
            // Secret-store outage is infrastructure, not a wrong code: must
            // surface as 503, never collapse into a 400/500. (Decrypt itself
            // is pure crypto and never throws this, but the guard documents
            // the contract if the port implementation ever changes.)
            throw unavailable;
        } catch (RuntimeException decryptFailed) {
            // Wrong rotation key or tampered row fails closed like a wrong
            // code (400 InvalidMfaCode), never a 500 that leaks the cause.
            throw new InvalidMfaCodeException();
        }
        if (!totpPolicy.verify(secret, code, clockProvider.clock())) {
            // 400, not the generic 401: this endpoint runs in an already
            // authenticated session (see InvalidMfaCodeException).
            throw new InvalidMfaCodeException();
        }
        user.enableMfa();
        saveUserPort.save(user);
        EnableMfaUseCaseImpl.audit(auditEventPort, AuditEvent.MFA_ENABLED, user,
                "MFA enrollment confirmed and enabled.", clockProvider);
    }
}
