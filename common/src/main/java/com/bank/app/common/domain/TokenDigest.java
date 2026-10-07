package com.bank.app.common.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * SHA-256 hex digests for token storage. Raw tokens (bearer, refresh) are
 * never persisted: revocation and session tables keep digests only, so a
 * database read never yields a usable credential.
 */
public final class TokenDigest {

    private TokenDigest() {
    }

    public static String sha256Hex(String token) {
        Objects.requireNonNull(token, "Token must not be null");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
