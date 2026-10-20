package com.bank.app.user.application.port.out;

/**
 * TOTP-secret encryption for MFA flows. Implemented by the AES-GCM adapter;
 * the application layer programs against this port so it never touches
 * adapter or config packages (see LayeringArchitectureTest).
 */
public interface MfaSecretEncryptorPort {
    String encrypt(byte[] plaintext);

    byte[] decrypt(String encryptedBase64);

    /**
     * Whether the stored ciphertext was written with the current primary
     * key. Callers use this to lazily re-encrypt rows that predate a key
     * rotation. Defaults to {@code true} for implementations without a key
     * ring.
     */
    default boolean isCurrentKey(String encryptedBase64) {
        return true;
    }
}
