package com.bank.app.user.application.port.out;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * Encrypted TOTP-secret store. Secrets are AES-GCM ciphertext (Base64) —
 * never plaintext: a database read must never yield an enrollable secret.
 * The flag ({@code User.isMfaEnabled}) travels with the aggregate; this port
 * only moves ciphertext.
 *
 * <p>The port also owns the TOTP replay counter (V56): a code is valid for
 * {@code 2 * skew + 1} steps, so the last accepted step counter is persisted
 * next to the secret and a replayed code is rejected. The counter travels
 * here — not through the {@code User} aggregate — because it is written by a
 * dedicated conditional statement that must not participate in the
 * aggregate's optimistic-lock save path.
 */
public interface MfaSecretPort {
    void saveSecret(Long userId, String encryptedBase64);

    Optional<String> findSecret(Long userId);

    void deleteSecret(Long userId);

    /**
     * Records the step counter of an accepted TOTP code.
     *
     * <p>The write is conditional: only a strictly newer counter is stored.
     * A replayed code therefore updates zero rows, which the caller must
     * treat as a rejection.
     *
     * @return {@code true} when the counter was accepted, {@code false} when
     *         the code was a replay (counter not newer than the stored one)
     */
    boolean recordAcceptedTotpCounter(Long userId, long counter);

    /** The last accepted TOTP step counter, or empty when none was recorded. */
    OptionalLong findLastAcceptedTotpCounter(Long userId);
}
