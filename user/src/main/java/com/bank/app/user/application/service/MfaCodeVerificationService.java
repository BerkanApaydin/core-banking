package com.bank.app.user.application.service;

import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.user.application.port.out.AuthenticationBackendUnavailableException;
import com.bank.app.user.application.port.out.MfaAttemptPort;
import com.bank.app.user.application.port.out.MfaSecretEncryptorPort;
import com.bank.app.user.application.port.out.MfaSecretPort;
import com.bank.app.user.domain.TotpPolicy;
import java.util.OptionalLong;

/**
 * Single place where a TOTP code is verified and CONSUMED.
 *
 * <p>Three call sites need the same three guarantees, so they share this
 * service instead of repeating the logic:
 * <ol>
 *   <li><b>Brute-force bound</b> — a failed code increments an
 *       ACCOUNT-keyed counter ({@link MfaAttemptPort}) that locks the
 *       account out after {@code app.security.mfa.max-attempts} failures.
 *       The IP-keyed rate limiter alone cannot contain a distributed guess;
 *       an account-keyed counter can, and it does not let one abusive client
 *       lock a legitimate user out of their own second factor. EVERY call
 *       site consults {@link #isBlocked} before verifying (login after the
 *       password check, the management endpoints per request), so the same
 *       account cannot keep guessing through a different endpoint.</li>
 *   <li><b>Replay protection</b> — the matched step counter is persisted
 *       (V56) and only a strictly newer counter is accepted, so a captured
 *       code cannot be reused inside its {@code 2 * skew + 1} step window.
 *       The database arbitrates, so two concurrent replays cannot both
 *       win.</li>
 *   <li><b>Uniform failure</b> — every rejection (missing secret, wrong
 *       code, replay) returns the same {@code false}. A caller decides how
 *       to render it; the login path keeps its generic 401 while the
 *       session-authenticated endpoints use their 400.</li>
 * </ol>
 *
 * <p>A secret-store outage is infrastructure, not a wrong code: it
 * propagates as {@link AuthenticationBackendUnavailableException} (503) and
 * is NOT counted as a failed attempt.
 */
public class MfaCodeVerificationService {

    private final MfaSecretPort mfaSecretPort;
    private final MfaSecretEncryptorPort encryptor;
    private final TotpPolicy totpPolicy;
    private final MfaAttemptPort mfaAttemptPort;
    private final ClockProviderPort clockProvider;

    public MfaCodeVerificationService(MfaSecretPort mfaSecretPort,
            MfaSecretEncryptorPort encryptor,
            TotpPolicy totpPolicy,
            MfaAttemptPort mfaAttemptPort,
            ClockProviderPort clockProvider) {
        this.mfaSecretPort = mfaSecretPort;
        this.encryptor = encryptor;
        this.totpPolicy = totpPolicy;
        this.mfaAttemptPort = mfaAttemptPort;
        this.clockProvider = clockProvider;
    }

    /** Whether the account is currently locked out by failed MFA attempts. */
    public boolean isBlocked(Long userId) {
        return userId != null && mfaAttemptPort.isBlocked(userId);
    }

    /** The lockout window in minutes (for the client-facing message). */
    public int getWindowMinutes() {
        return mfaAttemptPort.getWindowMinutes();
    }

    /**
     * Verifies the code and CONSUMES it: the matched step counter is
     * persisted, so the same code cannot be used twice.
     *
     * <p>Use this where the code MINTS something — the login path. A
     * captured (password, code) pair can then not open a second session
     * inside the code's validity window.
     *
     * <p>The enrollment-confirmation and MFA-removal paths use
     * {@link #verify} instead: they do not mint a session, and consuming
     * the counter there would reject the very next legitimate use of the
     * same code (confirm, then log in, inside one 30-second step).
     *
     * @return {@code true} when the code was valid, not a replay, and the
     *         account's counter was reset
     */
    public boolean verifyAndConsume(Long userId, String code) {
        return verify(userId, code, true);
    }

    /**
     * Verifies the code WITHOUT consuming it: the step counter is not
     * advanced, so the code stays usable for its whole validity window.
     *
     * <p>Use this for the session-authenticated management endpoints
     * (enrollment confirmation, MFA removal). Neither mints a session, so
     * neither needs replay protection — and consuming the counter would
     * break the "confirm, then log in" flow inside a single step.
     *
     * <p><b>Removal (accepted residual risk).</b> A code that was captured
     * and not yet consumed on the login path stays usable for the removal
     * endpoint inside its {@code 2 * skew + 1} step window. Disabling the
     * factor additionally requires the account password and an authenticated,
     * CSRF-protected session; the code alone is therefore not sufficient.
     * Consuming here instead would reject a legitimate "log in, then disable
     * MFA" sequence inside one 30-second step.
     */
    public boolean verify(Long userId, String code) {
        return verify(userId, code, false);
    }

    private boolean verify(Long userId, String code, boolean consume) {
        if (userId == null) {
            return false;
        }
        if (code == null || code.isBlank()) {
            mfaAttemptPort.recordFailure(userId);
            return false;
        }
        final String ciphertext = mfaSecretPort.findSecret(userId).orElse(null);
        if (ciphertext == null) {
            // Enrolled flag without a secret is a corrupt state: fail closed
            // rather than letting the factor silently pass.
            mfaAttemptPort.recordFailure(userId);
            return false;
        }
        final byte[] secret;
        try {
            secret = encryptor.decrypt(ciphertext);
        } catch (AuthenticationBackendUnavailableException unavailable) {
            // Secret-store outage is infrastructure (503), not a wrong code.
            throw unavailable;
        } catch (RuntimeException decryptFailed) {
            // Wrong rotation key / tampered row: fail closed like a wrong
            // code, never a 500 that leaks the cause.
            mfaAttemptPort.recordFailure(userId);
            return false;
        }
        OptionalLong counter = totpPolicy.verifyCounter(secret, code, clockProvider.clock());
        if (counter.isEmpty()) {
            mfaAttemptPort.recordFailure(userId);
            return false;
        }
        if (consume && !mfaSecretPort.recordAcceptedTotpCounter(userId, counter.getAsLong())) {
            // The stored counter was not older: this code was already used
            // inside its validity window. A replay is a failed attempt.
            mfaAttemptPort.recordFailure(userId);
            return false;
        }
        mfaAttemptPort.reset(userId);
        return true;
    }
}
