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
}
