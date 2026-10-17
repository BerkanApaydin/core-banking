package com.bank.app.user.adapter.out.security;

import com.bank.app.user.application.port.out.MfaSecretEncryptorPort;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM encryption for TOTP secrets at rest. Wire format is
 * {@code Base64(iv || ciphertext)} with a 12-byte random IV per encryption,
 * so identical secrets encrypt differently every time.
 *
 * <p>Keys come from {@code MfaProperties.encryptionKeyBase64}
 * ({@code MFA_ENCRYPTION_KEY}, 32 bytes). A blank key yields an ephemeral
 * in-memory key via {@link #ephemeral()} — dev/test convenience only (secrets
 * die with the JVM); production refuses to boot without a real key
 * (see {@code ApplicationStartupValidator}).
 */
public final class AesGcmMfaEncryptor implements MfaSecretEncryptorPort {

    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public AesGcmMfaEncryptor(byte[] keyBytes) {
        Objects.requireNonNull(keyBytes, "Key must not be null");
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("MFA encryption key must be 32 bytes (AES-256)");
        }
        this.key = new SecretKeySpec(keyBytes.clone(), "AES");
    }

    /** Resolves the configured key or falls back to an ephemeral one. */
    public static AesGcmMfaEncryptor fromBase64OrEphemeral(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            return ephemeral();
        }
        try {
            return new AesGcmMfaEncryptor(Base64.getDecoder().decode(base64Key.strip()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("MFA encryption key is not valid Base64", e);
        }
    }

    public static AesGcmMfaEncryptor ephemeral() {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        return new AesGcmMfaEncryptor(key);
    }

    @Override
    public String encrypt(byte[] plaintext) {
        return encryptToBase64(plaintext);
    }

    @Override
    public byte[] decrypt(String encryptedBase64) {
        return decryptFromBase64(encryptedBase64);
    }

    public String encryptToBase64(byte[] plaintext) {
        Objects.requireNonNull(plaintext, "Plaintext must not be null");
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
        } catch (Exception e) {
            throw new IllegalStateException("MFA secret encryption failed", e);
        }
    }

    public byte[] decryptFromBase64(String stored) {
        Objects.requireNonNull(stored, "Stored secret must not be null");
        try {
            byte[] raw = Base64.getDecoder().decode(stored);
            if (raw.length <= IV_BYTES) {
                throw new IllegalArgumentException("Stored MFA secret is truncated");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(GCM_TAG_BITS, raw, 0, IV_BYTES));
            return cipher.doFinal(raw, IV_BYTES, raw.length - IV_BYTES);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            // GCM tag mismatch (wrong key or tampered row) lands here:
            // fail closed without distinguishing the cause.
            throw new IllegalStateException("MFA secret decryption failed", e);
        }
    }
}
