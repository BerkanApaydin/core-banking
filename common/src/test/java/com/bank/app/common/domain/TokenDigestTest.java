package com.bank.app.common.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenDigestTest {

    @Test
    void shouldProduceStable64CharHexDigest() {
        String first = TokenDigest.sha256Hex("token");
        String second = TokenDigest.sha256Hex("token");

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64);
        assertThat(first).matches("[0-9a-f]+");
    }

    @Test
    void shouldDigestDistinctTokensDistinctly() {
        assertThat(TokenDigest.sha256Hex("token-a"))
                .isNotEqualTo(TokenDigest.sha256Hex("token-b"));
    }

    @Test
    void shouldRejectNull() {
        assertThatThrownBy(() -> TokenDigest.sha256Hex(null))
                .isExactlyInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldMatchStandardSha256Vectors() {
        // Pins the algorithm and UTF-8 encoding, not just self-consistency:
        // a different digest would silently invalidate every stored token hash.
        assertThat(TokenDigest.sha256Hex(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assertThat(TokenDigest.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void shouldDigestWhitespaceAndUnicodeStably() {
        assertThat(TokenDigest.sha256Hex(" ")).hasSize(64).isEqualTo(TokenDigest.sha256Hex(" "));
        assertThat(TokenDigest.sha256Hex("şifre-ü")).hasSize(64)
                .isEqualTo(TokenDigest.sha256Hex("şifre-ü"))
                .isNotEqualTo(TokenDigest.sha256Hex("sifre-u"));
    }
}
