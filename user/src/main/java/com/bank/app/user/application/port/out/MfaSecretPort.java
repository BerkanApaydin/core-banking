package com.bank.app.user.application.port.out;

import java.util.Optional;

/**
 * Encrypted TOTP-secret store. Secrets are AES-GCM ciphertext (Base64) —
 * never plaintext: a database read must never yield an enrollable secret.
 * The flag ({@code User.isMfaEnabled}) travels with the aggregate; this port
 * only moves ciphertext.
 */
public interface MfaSecretPort {
    void saveSecret(Long userId, String encryptedBase64);

    Optional<String> findSecret(Long userId);

    void deleteSecret(Long userId);
}
