package com.bank.app.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EncodedPassword")
class EncodedPasswordTest {

    @Test
    @DisplayName("should accept BCrypt hashes")
    void shouldAcceptBcryptHashes() {
        assertThat(EncodedPassword.of("$2a$10$abcdefghijklmnopqrstuu").value())
                .startsWith("$2a$");
        assertThat(EncodedPassword.of("$2b$12$abcdefghijklmnopqrstuu").value())
                .startsWith("$2b$");
        assertThat(EncodedPassword.of("$2y$10$abcdefghijklmnopqrstuu").value())
                .startsWith("$2y$");
    }

    @Test
    @DisplayName("should reject raw input")
    void shouldRejectRawInput() {
        assertThatThrownBy(() -> EncodedPassword.of("hashedpassword"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EncodedPassword.of("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EncodedPassword.of(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("ofTrusted should wrap legacy values without validation")
    void ofTrustedShouldWrapLegacy() {
        assertThat(EncodedPassword.ofTrusted("hashedpassword").value()).isEqualTo("hashedpassword");
    }

    @Test
    @DisplayName("should expose value and hash identically")
    void shouldExposeValueAndHash() {
        EncodedPassword password = EncodedPassword.of("$2a$10$abcdefghijklmnopqrstuu");

        assertThat(password.value()).isEqualTo("$2a$10$abcdefghijklmnopqrstuu");
        assertThat(password.hash()).isEqualTo(password.value());
        assertThat(password.toString()).doesNotContain("abcdefghijklmnopqrstuu");
    }

    @Test
    @DisplayName("toString must return the fixed mask, never the hash")
    void shouldMaskHashInToString() {
        // Kills the EmptyObjectReturn mutant ("" vs mask): the mask is a
        // security contract, not just "non-leaking".
        assertThat(EncodedPassword.of("$2a$10$abcdefghijklmnopqrstuu").toString())
                .isEqualTo("EncodedPassword{***}");
    }

    @Test
    @DisplayName("should implement value equality")
    void shouldImplementValueEquality() {
        assertThat(EncodedPassword.of("$2a$10$abcdefghijklmnopqrstuu"))
                .isEqualTo(EncodedPassword.of("$2a$10$abcdefghijklmnopqrstuu"))
                .hasSameHashCodeAs(EncodedPassword.of("$2a$10$abcdefghijklmnopqrstuu"));
        assertThat(EncodedPassword.of("$2a$10$abcdefghijklmnopqrstuu"))
                .isNotEqualTo(EncodedPassword.of("$2b$12$abcdefghijklmnopqrstuu"));
    }
}
