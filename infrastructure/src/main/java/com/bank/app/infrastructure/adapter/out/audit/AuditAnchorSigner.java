package com.bank.app.infrastructure.adapter.out.audit;

import com.bank.app.audit.application.dto.AuditAnchor;
import com.bank.app.infrastructure.adapter.in.audit.AuditAnchorProperties;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 signing for the external audit anchor. The key ring mirrors the
 * JWT rotation seam: new anchors are signed with the active key id, while
 * anchors signed by a retired key remain verifiable until they are re-anchored.
 * An unknown key id fails verification closed.
 */
public class AuditAnchorSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MIN_KEY_BYTES = 32;

    private final String activeKeyId;
    private final SecretKey activeKey;
    private final Map<String, SecretKey> retiredKeys;

    public AuditAnchorSigner(AuditAnchorProperties properties) {
        if (properties.secret() == null || properties.secret().isBlank()) {
            throw new IllegalStateException("Audit anchor secret must not be blank");
        }
        this.activeKeyId = properties.keyId();
        this.activeKey = decodeKey(properties.secret(), "Audit anchor secret");
        Map<String, SecretKey> decoded = new HashMap<>();
        properties.retiredKeys().forEach((keyId, secret) ->
                decoded.put(keyId, decodeKey(secret, "Audit anchor retired secret " + keyId)));
        this.retiredKeys = Map.copyOf(decoded);
    }

    public AuditAnchor sign(long lastSeq, String lastHash, Instant publishedAt) {
        long epochSecond = publishedAt.getEpochSecond();
        String payload = AuditAnchor.canonicalPayload(
                AuditAnchor.VERSION, activeKeyId, lastSeq, lastHash, epochSecond);
        String signature = base64Url(hmac(activeKey, payload));
        return new AuditAnchor(AuditAnchor.VERSION, activeKeyId, lastSeq,
                lastHash == null ? "" : lastHash, epochSecond, signature);
    }

    public boolean verify(AuditAnchor anchor) {
        if (anchor == null) {
            return false;
        }
        SecretKey key = activeKeyId.equals(anchor.keyId())
                ? activeKey
                : retiredKeys.get(anchor.keyId());
        if (key == null) {
            return false;
        }
        String expected = base64Url(hmac(key, anchor.signingPayload()));
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                anchor.signature().getBytes(StandardCharsets.US_ASCII));
    }

    private static SecretKey decodeKey(String value, String label) {
        final byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException invalidBase64) {
            throw new IllegalStateException(
                    label + " must be valid Base64 (generate: openssl rand -base64 32)",
                    invalidBase64);
        }
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    label + " must be at least 256 bits (32 bytes) when base64-decoded. "
                            + "Current key length: " + (keyBytes.length * 8) + " bits.");
        }
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    private static byte[] hmac(SecretKey key, String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // HmacSHA256 is mandatory in every JDK; InvalidKeyException cannot
            // happen for a validated non-empty raw key. Fail closed.
            throw new IllegalStateException("HMAC-SHA256 unavailable for audit anchor", e);
        }
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
