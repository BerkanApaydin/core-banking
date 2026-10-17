package com.bank.app.user.adapter.out.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AesGcmMfaEncryptorTest {

    private static final byte[] KEY = Base64.getDecoder().decode(
            "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=");

    @Test
    void shouldRoundTrip() {
        AesGcmMfaEncryptor encryptor = new AesGcmMfaEncryptor(KEY);
        byte[] plaintext = "test-secret-bytes-20!!".getBytes(StandardCharsets.UTF_8);

        String stored = encryptor.encryptToBase64(plaintext);

        assertArrayEquals(plaintext, encryptor.decryptFromBase64(stored));
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
    void shouldRejectBadKeySizes() {
        assertThrows(IllegalArgumentException.class, () -> new AesGcmMfaEncryptor(new byte[16]));
        assertThrows(NullPointerException.class, () -> new AesGcmMfaEncryptor(null));
        assertThrows(IllegalArgumentException.class,
                () -> AesGcmMfaEncryptor.fromBase64OrEphemeral("not-base64!!!"));
    }

    @Test
    void shouldFallBackToEphemeralOnBlankKey() {
        AesGcmMfaEncryptor ephemeral = AesGcmMfaEncryptor.fromBase64OrEphemeral("  ");
        byte[] plaintext = "x".getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(plaintext, ephemeral.decryptFromBase64(ephemeral.encryptToBase64(plaintext)));
    }

    @Test
    void shouldResolveConfiguredKey() {
        AesGcmMfaEncryptor encryptor = AesGcmMfaEncryptor.fromBase64OrEphemeral(
                "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=");
        byte[] plaintext = "y".getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(plaintext, encryptor.decryptFromBase64(encryptor.encryptToBase64(plaintext)));
    }
}
