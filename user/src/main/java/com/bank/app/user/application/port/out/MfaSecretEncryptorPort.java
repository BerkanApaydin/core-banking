package com.bank.app.user.application.port.out;

/**
 * TOTP-secret encryption for MFA flows. Implemented by the AES-GCM adapter;
 * the application layer programs against this port so it never touches
 * adapter or config packages (see LayeringArchitectureTest).
 */
public interface MfaSecretEncryptorPort {
    String encrypt(byte[] plaintext);

    byte[] decrypt(String encryptedBase64);
}
