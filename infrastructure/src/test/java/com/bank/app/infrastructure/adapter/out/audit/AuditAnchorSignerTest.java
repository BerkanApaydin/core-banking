package com.bank.app.infrastructure.adapter.out.audit;

import com.bank.app.audit.application.dto.AuditAnchor;
import com.bank.app.infrastructure.adapter.in.audit.AuditAnchorProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AuditAnchorSigner")
class AuditAnchorSignerTest {

    private static final String HASH = "a".repeat(64);
    private static final String OTHER_HASH = "b".repeat(64);
    private static final Instant NOW = Instant.parse("2026-10-29T03:45:00Z");
    private static final String ACTIVE_SECRET = secret("active-anchor-key");
    private static final String OLD_SECRET = secret("old-anchor-key");

    @Test
    @DisplayName("should sign and verify an anchor")
    void shouldSignAndVerify() {
        AuditAnchorSigner signer = new AuditAnchorSigner(properties("anchor-v1", ACTIVE_SECRET, Map.of()));

        AuditAnchor anchor = signer.sign(42, HASH, NOW);

        assertThat(anchor.keyId()).isEqualTo("anchor-v1");
        assertThat(anchor.lastSeq()).isEqualTo(42);
        assertThat(anchor.lastHash()).isEqualTo(HASH);
        assertThat(anchor.publishedAtEpochSecond()).isEqualTo(NOW.getEpochSecond());
        assertThat(anchor.signature()).hasSize(43);
        assertThat(signer.verify(anchor)).isTrue();
    }

    @Test
    @DisplayName("should reject a tampered anchor")
    void shouldRejectTamperedAnchor() {
        AuditAnchorSigner signer = new AuditAnchorSigner(properties("anchor-v1", ACTIVE_SECRET, Map.of()));
        AuditAnchor anchor = signer.sign(42, HASH, NOW);

        AuditAnchor tampered = new AuditAnchor(anchor.version(), anchor.keyId(),
                anchor.lastSeq(), OTHER_HASH, anchor.publishedAtEpochSecond(), anchor.signature());

        assertThat(signer.verify(tampered)).isFalse();
    }

    @Test
    @DisplayName("should reject an unknown key id")
    void shouldRejectUnknownKeyId() {
        AuditAnchorSigner signer = new AuditAnchorSigner(properties("anchor-v1", ACTIVE_SECRET, Map.of()));
        AuditAnchor anchor = signer.sign(42, HASH, NOW);
        AuditAnchor unknownKey = new AuditAnchor(anchor.version(), "other-key",
                anchor.lastSeq(), anchor.lastHash(), anchor.publishedAtEpochSecond(),
                anchor.signature());

        assertThat(signer.verify(unknownKey)).isFalse();
    }

    @Test
    @DisplayName("should verify an anchor signed by a retired key")
    void shouldVerifyRetiredKey() {
        AuditAnchorSigner oldSigner = new AuditAnchorSigner(
                properties("old-key", OLD_SECRET, Map.of()));
        AuditAnchor oldAnchor = oldSigner.sign(7, HASH, NOW);

        AuditAnchorSigner currentSigner = new AuditAnchorSigner(
                properties("anchor-v1", ACTIVE_SECRET, Map.of("old-key", OLD_SECRET)));

        assertThat(currentSigner.verify(oldAnchor)).isTrue();
    }

    @Test
    @DisplayName("should reject an invalid base64 secret")
    void shouldRejectInvalidBase64Secret() {
        assertThatThrownBy(() -> new AuditAnchorSigner(
                properties("anchor-v1", "not-base64!", Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    @DisplayName("should reject a secret shorter than 256 bits")
    void shouldRejectShortSecret() {
        String shortSecret = Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new AuditAnchorSigner(
                properties("anchor-v1", shortSecret, Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }

    private static AuditAnchorProperties properties(String keyId, String secret,
            Map<String, String> retiredKeys) {
        return new AuditAnchorProperties(true, "anchor.log", keyId, secret, retiredKeys);
    }

    private static String secret(String value) {
        byte[] key = Arrays.copyOf(value.getBytes(StandardCharsets.UTF_8), 32);
        return Base64.getEncoder().encodeToString(key);
    }
}
