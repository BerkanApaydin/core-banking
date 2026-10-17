package com.bank.app.user.domain;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Time-based one-time passwords (RFC 6238 / RFC 4226, HMAC-SHA1) with zero
 * dependencies — the MFA second factor.
 *
 * <p>Secrets are 20 random bytes (160 bits, the RFC 6238 recommendation),
 * exchanged with authenticator apps as Base32 without padding plus an
 * {@code otpauth://} URI. Verification accepts {@code allowedSkewWindows}
 * steps on either side of the current step so a code typed at a step boundary
 * (or with a moderately drifted phone clock) still passes, without widening
 * the replay window beyond {@code 2 * skew + 1} codes. Comparison is
 * constant-time so a wrong code reveals nothing about the expected one.
 *
 * <p>Replay within a step (same code twice) is NOT tracked here: login codes
 * are single-shot by construction (a successful login mints tokens and the
 * code is never stored), and enrollment/disable confirmations are
 * user-initiated retries of the same pending action.
 */
public final class Totp {

    /** Recommended secret size (RFC 6238 §4: at least 128 bits). */
    public static final int SECRET_BYTES = 20;
    /** Default TOTP step (seconds). */
    public static final int DEFAULT_PERIOD_SECONDS = 30;
    /** Default code length shown by authenticator apps. */
    public static final int DEFAULT_DIGITS = 6;

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** Generates a fresh random secret for enrollment. */
    public static byte[] generateSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        RANDOM.nextBytes(secret);
        return secret;
    }

    /** RFC 4648 Base32 without padding (what authenticator apps accept). */
    public static String base32Encode(byte[] data) {
        Objects.requireNonNull(data, "Data must not be null");
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                out.append(BASE32_ALPHABET.charAt((buffer >> bitsLeft) & 0x1F));
            }
        }
        if (bitsLeft > 0) {
            out.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return out.toString();
    }

    /** Decodes Base32 with or without padding (accepts lowercase). */
    public static byte[] base32Decode(String encoded) {
        Objects.requireNonNull(encoded, "Encoded secret must not be null");
        String clean = encoded.strip().replace("=", "").toUpperCase(Locale.ROOT);
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("Secret must not be empty");
        }
        int outLen = clean.length() * 5 / 8;
        byte[] out = new byte[outLen];
        int buffer = 0;
        int bitsLeft = 0;
        int pos = 0;
        for (char c : clean.toCharArray()) {
            int val = BASE32_ALPHABET.indexOf(c);
            if (val < 0) {
                throw new IllegalArgumentException("Invalid Base32 character: " + c);
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                out[pos++] = (byte) ((buffer >> bitsLeft) & 0xFF);
            }
        }
        return out;
    }

    /**
     * Builds the enrollment URI scanned by authenticator apps.
     * Format follows the Google Authenticator {@code otpauth://} key URI spec.
     * Uses the default digits/period ({@link #DEFAULT_DIGITS}/
     * {@link #DEFAULT_PERIOD_SECONDS}).
     */
    public static String provisioningUri(String issuer, String username, String base32Secret) {
        return provisioningUri(issuer, username, base32Secret, DEFAULT_DIGITS, DEFAULT_PERIOD_SECONDS);
    }

    /**
     * Builds the enrollment URI with explicit TOTP parameters. The digits and
     * period MUST match the server-side verification policy (see
     * {@code TotpPolicy}): authenticator apps derive their codes from this
     * URI, so a hardcoded 6/30 here while the server expects 8/60 would make
     * every enrollment fail permanently.
     */
    public static String provisioningUri(String issuer, String username, String base32Secret,
            int digits, int periodSeconds) {
        Objects.requireNonNull(issuer, "Issuer must not be null");
        Objects.requireNonNull(username, "Username must not be null");
        Objects.requireNonNull(base32Secret, "Secret must not be null");
        if (digits < 6 || digits > 8) {
            throw new IllegalArgumentException("Digits must be 6-8");
        }
        if (periodSeconds <= 0) {
            throw new IllegalArgumentException("Period must be positive");
        }
        return "otpauth://totp/" + urlEncode(issuer) + ":" + urlEncode(username)
                + "?secret=" + base32Secret
                + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1&digits=" + digits
                + "&period=" + periodSeconds;
    }

    /** Computes the code for an explicit counter (test seam + verification core). */
    public static String generateCode(byte[] secret, long counter, int digits) {
        Objects.requireNonNull(secret, "Secret must not be null");
        if (secret.length == 0) {
            throw new IllegalArgumentException("Secret must not be empty");
        }
        if (digits < 6 || digits > 8) {
            throw new IllegalArgumentException("Digits must be 6-8");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int mod = 1;
            for (int i = 0; i < digits; i++) {
                mod *= 10;
            }
            return String.format(Locale.ROOT, "%0" + digits + "d", binary % mod);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA1 is unavailable", e);
        }
    }

    /**
     * Verifies a user-supplied code against the current time.
     *
     * @param allowedSkewWindows accepted steps on either side of now
     *                           (boundary typing + phone clock drift)
     */
    public static boolean verify(byte[] secret, String code, Clock clock,
            int periodSeconds, int digits, int allowedSkewWindows) {
        Objects.requireNonNull(secret, "Secret must not be null");
        Objects.requireNonNull(clock, "Clock must not be null");
        if (code == null || periodSeconds <= 0 || allowedSkewWindows < 0) {
            return false;
        }
        String candidate = code.strip();
        if (candidate.length() != digits || !candidate.chars().allMatch(Character::isDigit)) {
            return false;
        }
        long step = clock.millis() / 1000L / periodSeconds;
        boolean ok = false;
        // Evaluate every candidate step and fold with OR (no early return):
        // the number of HMAC evaluations must not reveal which step matched.
        for (long counter = step - allowedSkewWindows; counter <= step + allowedSkewWindows; counter++) {
            ok |= constantTimeEquals(generateCode(secret, counter, digits), candidate);
        }
        return ok;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    private static String urlEncode(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~') {
                out.append(c);
            } else if (c == ' ') {
                out.append("%20");
            } else {
                out.append(String.format(Locale.ROOT, "%%%02X", (int) c));
            }
        }
        return out.toString();
    }
}
