package com.bank.app.infrastructure.adapter.in.idempotency;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Length-delimited namespace avoids collisions between user-supplied key components. */
public final class IdempotencyFingerprint {
    private IdempotencyFingerprint() {}

    public static String operationKey(String scope, String subject, String method, String uri, String clientKey) {
        MessageDigest digest = sha256();
        add(digest, "v1");
        add(digest, scope);
        add(digest, subject);
        add(digest, method);
        add(digest, uri);
        add(digest, clientKey);
        return "http_" + HexFormat.of().formatHex(digest.digest());
    }

    public static String requestHash(String method, String uri, String query, byte[] arguments) {
        MessageDigest digest = sha256();
        add(digest, method);
        add(digest, uri);
        add(digest, query);
        byte[] bytes = arguments == null ? new byte[0] : arguments;
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void add(MessageDigest digest, String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
