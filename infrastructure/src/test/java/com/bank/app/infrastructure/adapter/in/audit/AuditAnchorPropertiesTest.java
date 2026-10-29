package com.bank.app.infrastructure.adapter.in.audit;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AuditAnchorProperties")
class AuditAnchorPropertiesTest {

    // Derived at runtime (same helper as AuditAnchorSignerTest) so the fixture
    // never lands in the source as a secret-shaped Base64 literal, which the
    // gitleaks generic-api-key rule reports. See docs/decisions/secret-management.md.
    private static final String SECRET = secret("properties-anchor-key");

    @Test
    @DisplayName("should allow missing file and secret while disabled")
    void shouldAllowDisabledWithoutFileOrSecret() {
        AuditAnchorProperties properties = new AuditAnchorProperties(
                false, null, "anchor-v1", null, null);

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.retiredKeys()).isEmpty();
    }

    @Test
    @DisplayName("should require file and secret when enabled")
    void shouldRequireFileAndSecretWhenEnabled() {
        assertThatThrownBy(() -> new AuditAnchorProperties(
                true, " ", "anchor-v1", SECRET, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file");

        assertThatThrownBy(() -> new AuditAnchorProperties(
                true, "anchor.log", "anchor-v1", " ", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secret");
    }

    @Test
    @DisplayName("should reject a key id containing the separator")
    void shouldRejectSeparatorInKeyId() {
        assertThatThrownBy(() -> new AuditAnchorProperties(
                true, "anchor.log", "anchor|v1", SECRET, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key id");
    }

    @Test
    @DisplayName("should reject a retired key that collides with the active key")
    void shouldRejectRetiredKeyCollision() {
        assertThatThrownBy(() -> new AuditAnchorProperties(
                true, "anchor.log", "anchor-v1", SECRET, Map.of("anchor-v1", SECRET)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retired keys");
    }

    private static String secret(String value) {
        byte[] key = Arrays.copyOf(value.getBytes(StandardCharsets.UTF_8), 32);
        return Base64.getEncoder().encodeToString(key);
    }
}
