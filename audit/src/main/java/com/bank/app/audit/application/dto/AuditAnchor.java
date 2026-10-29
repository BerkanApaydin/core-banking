package com.bank.app.audit.application.dto;

import java.util.regex.Pattern;

/**
 * A signed snapshot of the audit chain head exported outside the database.
 *
 * <p>The anchor is deliberately small and text-serialisable: it contains the
 * head sequence, the head hash, a publication timestamp and an HMAC-SHA256
 * signature. An external append-only/WORM store can retain one line per
 * publication; possession of an older anchor proves that a later database
 * state cannot be a legitimate continuation of the anchored chain.
 *
 * <p>The signature covers {@link #signingPayload()} and is base64url encoded
 * without padding (43 characters for a 32-byte HMAC).
 */
public record AuditAnchor(
        int version,
        String keyId,
        long lastSeq,
        String lastHash,
        long publishedAtEpochSecond,
        String signature
) {

    public static final int VERSION = 1;

    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SIGNATURE = Pattern.compile("[A-Za-z0-9_-]{43}");

    public AuditAnchor {
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported audit anchor version: " + version);
        }
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("Audit anchor keyId must not be blank");
        }
        if (keyId.indexOf('|') >= 0) {
            throw new IllegalArgumentException("Audit anchor keyId must not contain '|'");
        }
        if (lastSeq < 0) {
            throw new IllegalArgumentException("Audit anchor lastSeq must not be negative");
        }
        if (lastHash == null) {
            lastHash = "";
        }
        if (lastHash.isEmpty() && lastSeq != 0) {
            throw new IllegalArgumentException("A non-zero audit anchor must include lastHash");
        }
        if (!lastHash.isEmpty() && !HASH.matcher(lastHash).matches()) {
            throw new IllegalArgumentException(
                    "Audit anchor lastHash must be 64 lowercase hex characters");
        }
        if (publishedAtEpochSecond < 0) {
            throw new IllegalArgumentException("Audit anchor timestamp must not be negative");
        }
        if (signature == null || !SIGNATURE.matcher(signature).matches()) {
            throw new IllegalArgumentException(
                    "Audit anchor signature must be 43 base64url characters");
        }
    }

    /**
     * Canonical bytes signed by the anchor key. The field order and separator
     * are part of the audit contract; changing them invalidates every stored
     * anchor and must be treated as a new anchor version.
     */
    public String signingPayload() {
        return canonicalPayload(version, keyId, lastSeq, lastHash, publishedAtEpochSecond);
    }

    public static String canonicalPayload(int version, String keyId, long lastSeq,
                                          String lastHash, long publishedAtEpochSecond) {
        return version + "|" + keyId + "|" + lastSeq + "|"
                + (lastHash == null ? "" : lastHash) + "|" + publishedAtEpochSecond;
    }

    /**
     * One line of the append-only anchor file.
     */
    public String toLine() {
        return signingPayload() + "|" + signature;
    }
}
