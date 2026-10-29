package com.bank.app.audit.application.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AuditAnchor")
class AuditAnchorTest {

    private static final String HASH = "a".repeat(64);
    private static final String SIGNATURE = "A".repeat(43);

    @Test
    @DisplayName("should serialize a canonical signing payload and line")
    void shouldSerializeCanonicalPayload() {
        AuditAnchor anchor = new AuditAnchor(1, "anchor-v1", 42, HASH, 1_790_000_000L, SIGNATURE);

        assertThat(anchor.signingPayload())
                .isEqualTo("1|anchor-v1|42|" + HASH + "|1790000000");
        assertThat(anchor.toLine())
                .isEqualTo("1|anchor-v1|42|" + HASH + "|1790000000|" + SIGNATURE);
    }

    @Test
    @DisplayName("should accept a genesis anchor with an empty hash")
    void shouldAcceptGenesisAnchor() {
        AuditAnchor anchor = new AuditAnchor(1, "anchor-v1", 0, "", 1_790_000_000L, SIGNATURE);

        assertThat(anchor.lastHash()).isEmpty();
    }

    @Test
    @DisplayName("should reject an unsupported version")
    void shouldRejectUnsupportedVersion() {
        assertThatThrownBy(() -> new AuditAnchor(2, "anchor-v1", 0, "", 1L, SIGNATURE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
    }

    @Test
    @DisplayName("should reject a key id containing the field separator")
    void shouldRejectSeparatorInKeyId() {
        assertThatThrownBy(() -> new AuditAnchor(1, "anchor|v1", 0, "", 1L, SIGNATURE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keyId");
    }

    @Test
    @DisplayName("should reject a non-zero anchor without a hash")
    void shouldRejectMissingHash() {
        assertThatThrownBy(() -> new AuditAnchor(1, "anchor-v1", 1, "", 1L, SIGNATURE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lastHash");
    }

    @Test
    @DisplayName("should reject a malformed hash")
    void shouldRejectMalformedHash() {
        assertThatThrownBy(() -> new AuditAnchor(1, "anchor-v1", 1, "not-a-hash", 1L, SIGNATURE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lastHash");
    }

    @Test
    @DisplayName("should reject a malformed signature")
    void shouldRejectMalformedSignature() {
        assertThatThrownBy(() -> new AuditAnchor(1, "anchor-v1", 1, HASH, 1L, "short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature");
    }
}
