package com.bank.app.user.application.usecase;

import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.port.in.TransactionalUseCase;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.user.application.dto.MfaEnrollment;
import com.bank.app.user.application.port.in.EnableMfaUseCase;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.MfaSecretEncryptorPort;
import com.bank.app.user.application.port.out.MfaSecretPort;
import com.bank.app.user.application.service.MfaReauthenticationService;
import com.bank.app.user.domain.Totp;
import com.bank.app.user.domain.TotpPolicy;
import com.bank.app.user.domain.User;
import com.bank.app.user.domain.exception.MfaAlreadyEnabledException;
import com.bank.app.user.domain.exception.UserNotFoundException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;

/**
 * Starts TOTP enrollment: mints a fresh secret, stores it encrypted, and
 * returns the authenticator material. MFA stays OFF until
 * {@code ConfirmMfaUseCase} proves possession — a half-finished enrollment
 * (user never scans the QR) must never lock the user out.
 */
@TransactionalUseCase
public class EnableMfaUseCaseImpl implements EnableMfaUseCase {

    private final LoadUserPort loadUserPort;
    private final MfaReauthenticationService mfaReauthentication;
    private final MfaSecretPort mfaSecretPort;
    private final MfaSecretEncryptorPort encryptor;
    private final String issuer;
    private final int digits;
    private final int periodSeconds;
    private final ClockProviderPort clockProvider;
    private final AuditEventPort auditEventPort;

    public EnableMfaUseCaseImpl(LoadUserPort loadUserPort,
            MfaReauthenticationService mfaReauthentication,
            MfaSecretPort mfaSecretPort, MfaSecretEncryptorPort encryptor, String issuer,
            ClockProviderPort clockProvider, AuditEventPort auditEventPort) {
        this(loadUserPort, mfaReauthentication, mfaSecretPort, encryptor, issuer,
                Totp.DEFAULT_DIGITS, Totp.DEFAULT_PERIOD_SECONDS, clockProvider, auditEventPort);
    }

    public EnableMfaUseCaseImpl(LoadUserPort loadUserPort,
            MfaReauthenticationService mfaReauthentication,
            MfaSecretPort mfaSecretPort, MfaSecretEncryptorPort encryptor, String issuer,
            int digits, int periodSeconds,
            ClockProviderPort clockProvider, AuditEventPort auditEventPort) {
        this.loadUserPort = loadUserPort;
        this.mfaReauthentication = mfaReauthentication;
        this.mfaSecretPort = mfaSecretPort;
        this.encryptor = encryptor;
        this.issuer = issuer;
        this.digits = digits;
        this.periodSeconds = periodSeconds;
        this.clockProvider = clockProvider;
        this.auditEventPort = auditEventPort;
    }

    /** Policy-wired factory: keeps the otpauth URI in sync with verification. */
    public static EnableMfaUseCaseImpl withPolicy(LoadUserPort loadUserPort,
            MfaReauthenticationService mfaReauthentication, MfaSecretPort mfaSecretPort,
            MfaSecretEncryptorPort encryptor, String issuer, TotpPolicy totpPolicy,
            ClockProviderPort clockProvider, AuditEventPort auditEventPort) {
        return new EnableMfaUseCaseImpl(loadUserPort, mfaReauthentication, mfaSecretPort,
                encryptor, issuer, totpPolicy.digits(), totpPolicy.periodSeconds(),
                clockProvider, auditEventPort);
    }

    @Override
    public MfaEnrollment execute(Long userId, String password) {
        Objects.requireNonNull(userId, "User ID must not be null");
        User user = loadUserPort.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        if (user.isMfaEnabled()) {
            throw new MfaAlreadyEnabledException();
        }
        // Step-up before minting password-equivalent material: a stolen
        // session alone must not be able to enroll an attacker's secret.
        // R-U08: wrong passwords are charged to the shared account-keyed
        // verification budget; no second factor exists yet, so a successful
        // step-up clears it.
        mfaReauthentication.reauthenticate(userId, user.getUsername(), password, "MFA enable");
        mfaReauthentication.resetAttempts(userId);
        byte[] secret = Totp.generateSecret();
        try {
            mfaSecretPort.saveSecret(userId, encryptor.encrypt(secret));
            audit(auditEventPort, AuditEvent.MFA_ENABLED, user,
                    "MFA enrollment started (pending confirmation).", clockProvider);
            String base32 = Totp.base32Encode(secret);
            return new MfaEnrollment(
                    Totp.provisioningUri(issuer, user.getUsername(), base32, digits, periodSeconds), base32);
        } finally {
            // Plaintext seed must not linger on the heap after enrollment
            // material is built (heap-dump exposure).
            Arrays.fill(secret, (byte) 0);
        }
    }

    static void audit(AuditEventPort auditEventPort, String action, User user, String details,
            ClockProviderPort clockProvider) {
        try {
            auditEventPort.publishBestEffort(new AuditEvent(action, details,
                    LocalDateTime.now(clockProvider.clock()),
                    user.getUsername(), user.getId() != null ? user.getId().value() : null));
        } catch (Exception ignored) {
            // Best-effort like login audit: enrollment must not fail on audit noise.
        }
    }
}
