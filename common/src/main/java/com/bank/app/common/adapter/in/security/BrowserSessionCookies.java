package com.bank.app.common.adapter.in.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Shared cookie names and CSRF header without coupling bounded-context adapters.
 * Servlet-free on purpose (the shared kernel must not import the servlet API):
 * callers pass extracted header/cookie strings to {@link #validCsrfPair}.
 */
public record BrowserSessionCookies(boolean secure) {
    public String sessionCookieName() {
        return secure ? "__Host-BANK_SESSION" : "BANK_SESSION";
    }

    public String refreshCookieName() {
        return secure ? "__Host-BANK_REFRESH" : "BANK_REFRESH";
    }

    public String csrfCookieName() {
        return secure ? "__Host-BANK_CSRF" : "BANK_CSRF";
    }

    public static final String CSRF_HEADER = "X-CSRF-Token";

    /** Safe methods never mutate: GET, HEAD, OPTIONS, TRACE. */
    public static boolean requiresCsrf(String method) {
        return !switch (method) {
            case "GET", "HEAD", "OPTIONS", "TRACE" -> true;
            default -> false;
        };
    }

    /**
     * Double-submit check: the JavaScript-readable CSRF cookie value must
     * equal the {@code X-CSRF-Token} header using a constant-time comparison
     * over exactly 43 base64url characters (32 random bytes, no padding).
     */
    public static boolean validCsrfPair(String header, String cookie) {
        if (header == null || cookie == null || header.length() != 43 || cookie.length() != 43) {
            return false;
        }
        return MessageDigest.isEqual(header.getBytes(StandardCharsets.US_ASCII),
                cookie.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Session-bound CSRF tokens (K7/D8, OWASP signed double-submit).
     *
     * <p>Format: {@code base64url(rand32) + "." + base64url(HMAC(macKey,
     * boundCredential + "." + base64url(rand32)))} — 43 + 1 + 43 = 87 chars.
     * The MAC binds the token to an HttpOnly credential the attacker cannot
     * read (the session JWT on API paths, the refresh token on the refresh
     * path). A cookie-injection attacker can overwrite {@code BANK_CSRF} but
     * cannot mint a MAC without the credential, so the forged pair fails even
     * though header == cookie.
     *
     * <p>JDK-only ({@code javax.crypto} is {@code java.base}); servlet-free
     * like the rest of this type.
     */
    public static final int BOUND_TOKEN_LENGTH = 87;

    public static String boundToken(byte[] random32, byte[] macKey, String boundCredential) {
        if (random32 == null || random32.length != 32) {
            throw new IllegalArgumentException("random32 must be exactly 32 bytes");
        }
        if (macKey == null || macKey.length == 0) {
            throw new IllegalArgumentException("macKey must not be empty");
        }
        if (boundCredential == null || boundCredential.isBlank()) {
            throw new IllegalArgumentException("boundCredential must not be blank");
        }
        String rand = Base64.getUrlEncoder().withoutPadding().encodeToString(random32);
        String mac = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hmac(macKey, boundCredential + "." + rand));
        return rand + "." + mac;
    }

    public static boolean validBoundPair(String header, String cookie,
                                         String boundCredential, byte[] macKey) {
        if (header == null || cookie == null || boundCredential == null || boundCredential.isBlank()
                || macKey == null || macKey.length == 0) {
            return false;
        }
        if (header.length() != BOUND_TOKEN_LENGTH || cookie.length() != BOUND_TOKEN_LENGTH) {
            return false;
        }
        if (!MessageDigest.isEqual(header.getBytes(StandardCharsets.US_ASCII),
                cookie.getBytes(StandardCharsets.US_ASCII))) {
            return false;
        }
        int dot = cookie.indexOf('.');
        if (dot != 43) {
            return false;
        }
        String rand = cookie.substring(0, dot);
        String expectedMac = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hmac(macKey, boundCredential + "." + rand));
        return MessageDigest.isEqual(cookie.substring(dot + 1).getBytes(StandardCharsets.US_ASCII),
                expectedMac.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] hmac(byte[] key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            // HmacSHA256 is mandatory in every JDK; InvalidKeyException cannot
            // happen for non-empty raw keys. Fail closed, never fall back.
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
