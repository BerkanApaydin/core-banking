package com.bank.app.user.adapter.out.security;

import com.bank.app.user.application.port.out.MfaSecretEncryptorPort;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM encryption for TOTP secrets at rest, with a key ring for
 * rotation.
 *
 * <p>
 * New ciphertexts use {@code v1:<keyId>:<Base64(iv || ciphertext)>}; legacy
 * rows written before key ids existed remain plain {@code Base64(iv ||
 * ciphertext)} and are decrypted by trying the primary key and then each
 * configured previous key. A successful login/confirm re-encrypts legacy rows
 * with the current key ({@code MfaCodeVerificationService}), so rotation
 * completes lazily without a downtime migration.
 *
 * <p>
 * Keys come from {@code MfaProperties.encryptionKeyBase64}
 * ({@code MFA_ENCRYPTION_KEY}, 32 bytes) and
 * {@code previousEncryptionKeysBase64}
 * ({@code MFA_ENCRYPTION_PREVIOUS_KEYS}). A blank primary key yields an
 * ephemeral in-memory key via {@link #ephemeral()} — dev/test convenience only
 * (secrets die with the JVM); production refuses to boot without a real key
 * (see {@code ProductionConfigValidator}).
 */
public final class AesGcmMfaEncryptor implements MfaSecretEncryptorPort {

    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final String KEY_VERSION_PREFIX = "v1:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec primaryKey;
    private final String primaryKeyId;
    private final Map<String, SecretKeySpec> keysById;
    private final List<SecretKeySpec> decryptionKeys;

    public AesGcmMfaEncryptor(byte[] keyBytes) {
        this(keyBytes, List.of());
    }

    public AesGcmMfaEncryptor(byte[] primaryKeyBytes, List<byte[]> previousKeyBytes) {
        Objects.requireNonNull(primaryKeyBytes, "Key must not be null");
        Objects.requireNonNull(previousKeyBytes, "Previous keys must not be null");
        this.primaryKey = keySpec(primaryKeyBytes);
        this.primaryKeyId = keyId(primaryKeyBytes);
        Map<String, SecretKeySpec> byId = new LinkedHashMap<>();
        List<SecretKeySpec> all = new ArrayList<>();
        byId.put(primaryKeyId, primaryKey);
        all.add(primaryKey);
        for (byte[] previous : previousKeyBytes) {
            Objects.requireNonNull(previous, "Previous key must not be null");
            SecretKeySpec spec = keySpec(previous);
            String id = keyId(previous);
            if (byId.putIfAbsent(id, spec) != null) {
                throw new IllegalArgumentException("Duplicate MFA encryption key in the key ring: " + id);
            }
            all.add(spec);
        }
        this.keysById = Map.copyOf(byId);
        this.decryptionKeys = List.copyOf(all);
    }

    /** Resolves the configured key or falls back to an ephemeral one. */
    public static AesGcmMfaEncryptor fromBase64OrEphemeral(String base64Key) {
        return fromBase64OrEphemeral(base64Key, null);
    }

    /**
     * Resolves the primary key plus an optional comma-separated previous-key list.
     */
    public static AesGcmMfaEncryptor fromBase64OrEphemeral(String base64Key, String previousKeysCsv) {
        if (base64Key == null || base64Key.isBlank()) {
            return ephemeral();
        }
        try {
            List<byte[]> previous = new ArrayList<>();
            if (previousKeysCsv != null && !previousKeysCsv.isBlank()) {
                for (String previousKey : previousKeysCsv.split(",")) {
                    if (!previousKey.isBlank()) {
                        previous.add(Base64.getDecoder().decode(previousKey.strip()));
                    }
                }
            }
            return new AesGcmMfaEncryptor(Base64.getDecoder().decode(base64Key.strip()), previous);
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

    @Override
    public boolean isCurrentKey(String encryptedBase64) {
        if (encryptedBase64 == null || !encryptedBase64.startsWith(KEY_VERSION_PREFIX)) {
            return false;
        }
        int separator = encryptedBase64.indexOf(':', KEY_VERSION_PREFIX.length());
        if (separator == -1) {
            return false;
        }
        return primaryKeyId.equals(encryptedBase64.substring(KEY_VERSION_PREFIX.length(), separator));
    }

    public String encryptToBase64(byte[] plaintext) {
        Objects.requireNonNull(plaintext, "Plaintext must not be null");
        return KEY_VERSION_PREFIX + primaryKeyId + ":" + encryptWith(primaryKey, plaintext);
    }

    public byte[] decryptFromBase64(String stored) {
        Objects.requireNonNull(stored, "Stored secret must not be null");
        if (stored.startsWith(KEY_VERSION_PREFIX)) {
            int separator = stored.indexOf(':', KEY_VERSION_PREFIX.length());
            if (separator == -1) {
                throw new IllegalArgumentException("Stored MFA secret has a malformed key id");
            }
            String keyId = stored.substring(KEY_VERSION_PREFIX.length(), separator);
            SecretKeySpec key = keysById.get(keyId);
            if (key == null) {
                throw new IllegalStateException("MFA secret was encrypted with an unknown key id");
            }
            return decryptWith(key, stored.substring(separator + 1));
        }
        // Legacy format: no key id. Try the primary key first, then previous
        // keys so rotation does not strand existing enrollments.
        RuntimeException lastFailure = null;
        for (SecretKeySpec key : decryptionKeys) {
            try {
                return decryptWith(key, stored);
            } catch (IllegalArgumentException malformed) {
                // Malformed Base64/truncated input is key-independent: do not
                // mask it as a wrong-key failure.
                throw malformed;
            } catch (RuntimeException failure) {
                lastFailure = failure;
            }
        }
        throw new IllegalStateException("MFA secret decryption failed", lastFailure);
    }

    private String encryptWith(SecretKeySpec key, byte[] plaintext) {
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

    private byte[] decryptWith(SecretKeySpec key, String encryptedBase64) {
        try {
            byte[] raw = Base64.getDecoder().decode(encryptedBase64);
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

    private static SecretKeySpec keySpec(byte[] keyBytes) {
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("MFA encryption key must be 32 bytes (AES-256)");
        }
        return new SecretKeySpec(keyBytes.clone(), "AES");
    }

    private static String keyId(byte[] keyBytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(keyBytes);
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
