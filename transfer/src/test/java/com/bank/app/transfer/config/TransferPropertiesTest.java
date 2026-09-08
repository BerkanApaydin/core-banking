package com.bank.app.transfer.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferPropertiesTest {

    @Test
    void shouldRejectNonPositiveMaxAttempts() {
        assertThatThrownBy(() -> new TransferProperties(24, 0, 500L, 2000L, 100))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAttempts");
    }

    @Test
    void shouldAcceptValidProperties() {
        TransferProperties props = new TransferProperties(24, 3, 500L, 2000L, 100);

        assertThat(props.maxAttempts()).isEqualTo(3);
    }
}
