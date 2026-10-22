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
        // Exactly an IV with no ciphertext/tag is the largest truncated input.
        assertThrows(IllegalArgumentException.class,
                () -> encryptor.decryptFromBase64(Base64.getEncoder().encodeToString(new byte[12])));
    }

    @Test
    void shouldGenerateIndependentEphemeralKeys() {
        AesGcmMfaEncryptor first = AesGcmMfaEncryptor.ephemeral();
        AesGcmMfaEncryptor second = AesGcmMfaEncryptor.ephemeral();
        String stored = first.encryptToBase64("secret".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalStateException.class, () -> second.decryptFromBase64(stored));
    }

    @Test
    void shouldRejectVersionPrefixWithoutKeyIdSeparator() {
        AesGcmMfaEncryptor encryptor = new AesGcmMfaEncryptor(KEY);

        assertFalse(encryptor.isCurrentKey("v1:no-separator"));
        assertThrows(IllegalArgumentException.class,
                () -> encryptor.decryptFromBase64("v1:no-separator"));
    }

    @Test
    void shouldFailClosedWhenCiphertextIsTampered() {
        AesGcmMfaEncryptor encryptor = new AesGcmMfaEncryptor(KEY);
        String stored = encryptor.encryptToBase64("secret".getBytes(StandardCharsets.UTF_8));
        int separator = stored.indexOf(':', 3);
        byte[] raw = Base64.getDecoder().decode(stored.substring(separator + 1));
        raw[raw.length - 1] ^= 0x01;

        String tampered = stored.substring(0, separator + 1)
                + Base64.getEncoder().encodeToString(raw);

        assertThrows(IllegalStateException.class, () -> encryptor.decryptFromBase64(tampered));
    }

    @Test
    void shouldTreatNullOrLegacyValueAsNotCurrentKey() {
        AesGcmMfaEncryptor encryptor = new AesGcmMfaEncryptor(KEY);

        assertFalse(encryptor.isCurrentKey(null));
        assertFalse(encryptor.isCurrentKey("legacy-ciphertext-without-version"));
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
    void shouldLoadPreviousKeyFromCsvAndSkipBlankEntries() {
        String primary = "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=";
        String previous = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";
        AesGcmMfaEncryptor old = new AesGcmMfaEncryptor(KEY);
        String legacy = old.encryptToBase64("legacy".getBytes(StandardCharsets.UTF_8));
        legacy = legacy.substring(legacy.indexOf(':', 3) + 1);

        AesGcmMfaEncryptor rotated = AesGcmMfaEncryptor.fromBase64OrEphemeral(
                primary, " , " + previous + " , ");

        assertArrayEquals("legacy".getBytes(StandardCharsets.UTF_8),
                rotated.decryptFromBase64(legacy));
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
