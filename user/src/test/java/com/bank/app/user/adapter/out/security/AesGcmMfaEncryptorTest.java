package com.bank.app.user.adapter.out.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AesGcmMfaEncryptorTest {

    private static final byte[] KEY = Base64.getDecoder().decode(
            "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=");
    private static final byte[] OTHER_KEY = Base64.getDecoder().decode(
            "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=");

    @Test
    void shouldRoundTripWithKeyIdPrefix() {
        AesGcmMfaEncryptor encryptor = new AesGcmMfaEncryptor(KEY);
        byte[] plaintext = "test-secret-bytes-20!!".getBytes(StandardCharsets.UTF_8);

        String stored = encryptor.encryptToBase64(plaintext);

        assertTrue(stored.startsWith("v1:"), stored);
        assertEquals(3, stored.split(":").length);
        assertArrayEquals(plaintext, encryptor.decryptFromBase64(stored));
        assertTrue(encryptor.isCurrentKey(stored));
    }

    @Test
    void shouldRandomizeCiphertext() {
        AesGcmMfaEncryptor encryptor = new AesGcmMfaEncryptor(KEY);
        byte[] plaintext = "same-plaintext".getBytes(StandardCharsets.UTF_8);

        assertNotEquals(encryptor.encryptToBase64(plaintext), encryptor.encryptToBase64(plaintext));
    }

    @Test
    void shouldFailClosedOnWrongKey() {
        AesGcmMfaEncryptor encryptor = new AesGcmMfaEncryptor(KEY);
        String stored = encryptor.encryptToBase64("secret".getBytes(StandardCharsets.UTF_8));
        byte[] other = new byte[32];
        other[0] = 1;

        assertThrows(IllegalStateException.class,
                () -> new AesGcmMfaEncryptor(other).decryptFromBase64(stored));
    }

    @Test
    void shouldRejectTruncatedInput() {
        AesGcmMfaEncryptor encryptor = new AesGcmMfaEncryptor(KEY);

        assertThrows(IllegalArgumentException.class,
                () -> encryptor.decryptFromBase64(Base64.getEncoder().encodeToString(new byte[4])));
    }

    @Test
    void shouldRejectUnknownKeyId() {
        AesGcmMfaEncryptor encryptor = new AesGcmMfaEncryptor(KEY);
        String stored = encryptor.encryptToBase64("secret".getBytes(StandardCharsets.UTF_8));
        String tampered = "v1:0000000000000000:" + stored.substring(stored.indexOf(':', 3) + 1);

        assertThrows(IllegalStateException.class, () -> encryptor.decryptFromBase64(tampered));
    }

    @Test
    void shouldRejectBadKeySizes() {
        assertThrows(IllegalArgumentException.class, () -> new AesGcmMfaEncryptor(new byte[16]));
        assertThrows(NullPointerException.class, () -> new AesGcmMfaEncryptor(null));
        assertThrows(IllegalArgumentException.class,
                () -> AesGcmMfaEncryptor.fromBase64OrEphemeral("not-base64!!!"));
    }

    @Test
    void shouldRejectDuplicateKeyInRing() {
        assertThrows(IllegalArgumentException.class,
                () -> new AesGcmMfaEncryptor(KEY, List.of(KEY.clone())));
    }

    @Test
    void shouldFallBackToEphemeralOnBlankKey() {
        AesGcmMfaEncryptor ephemeral = AesGcmMfaEncryptor.fromBase64OrEphemeral("  ");
        byte[] plaintext = "x".getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(plaintext, ephemeral.decryptFromBase64(ephemeral.encryptToBase64(plaintext)));
    }

    @Test
    void shouldResolveConfiguredKeyAndPreviousKeys() {
        AesGcmMfaEncryptor encryptor = AesGcmMfaEncryptor.fromBase64OrEphemeral(
                "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
                "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=");
        byte[] plaintext = "y".getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(plaintext, encryptor.decryptFromBase64(encryptor.encryptToBase64(plaintext)));
    }

    @Test
    void shouldDecryptWithPreviousKeyAndReencryptWithPrimary() {
        AesGcmMfaEncryptor old = new AesGcmMfaEncryptor(KEY);
        byte[] plaintext = "rotation-secret".getBytes(StandardCharsets.UTF_8);
        String oldCiphertext = old.encryptToBase64(plaintext);

        AesGcmMfaEncryptor rotated = new AesGcmMfaEncryptor(OTHER_KEY, List.of(KEY));

        assertFalse(rotated.isCurrentKey(oldCiphertext));
        assertArrayEquals(plaintext, rotated.decryptFromBase64(oldCiphertext));

        String newCiphertext = rotated.encryptToBase64(plaintext);
        assertTrue(rotated.isCurrentKey(newCiphertext));
        assertNotEquals(oldCiphertext.substring(3, oldCiphertext.indexOf(':', 3)),
                newCiphertext.substring(3, newCiphertext.indexOf(':', 3)));
        assertThrows(IllegalStateException.class, () -> old.decryptFromBase64(newCiphertext));
    }

    @Test
    void shouldDecryptLegacyCiphertextWithoutKeyId() {
        AesGcmMfaEncryptor old = new AesGcmMfaEncryptor(KEY);
        byte[] plaintext = "legacy-secret".getBytes(StandardCharsets.UTF_8);
        String prefixed = old.encryptToBase64(plaintext);
        String legacy = prefixed.substring(prefixed.indexOf(':', 3) + 1);

        AesGcmMfaEncryptor rotated = new AesGcmMfaEncryptor(OTHER_KEY, List.of(KEY));

        assertFalse(rotated.isCurrentKey(legacy));
        assertArrayEquals(plaintext, rotated.decryptFromBase64(legacy));
        assertArrayEquals(plaintext, new AesGcmMfaEncryptor(KEY).decryptFromBase64(legacy));
    }

    @Test
    void shouldFailClosedOnLegacyCiphertextNoKnownKeyCanOpen() {
        AesGcmMfaEncryptor unknown = new AesGcmMfaEncryptor(OTHER_KEY);
        String prefixed = unknown.encryptToBase64("secret".getBytes(StandardCharsets.UTF_8));
        String legacy = prefixed.substring(prefixed.indexOf(':', 3) + 1);

        assertThrows(IllegalStateException.class,
                () -> new AesGcmMfaEncryptor(KEY).decryptFromBase64(legacy));
    }
}
