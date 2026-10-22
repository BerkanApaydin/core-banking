package com.bank.app.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RequestBodyLimitProperties edge cases")
class RequestBodyLimitPropertiesEdgeCaseTest {

    @Test
    @DisplayName("accepts the 1KB floor exactly")
    void shouldAcceptMinimumSize() {
        assertThat(new RequestBodyLimitProperties(1024).maxBytes()).isEqualTo(1024);
    }

    @Test
    @DisplayName("rejects sizes below 1KB")
    void shouldRejectSizesBelowTheFloor() {
        assertThatThrownBy(() -> new RequestBodyLimitProperties(1023))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 1KB");
        assertThatThrownBy(() -> new RequestBodyLimitProperties(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 1KB");
        assertThatThrownBy(() -> new RequestBodyLimitProperties(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 1KB");
    }
}
