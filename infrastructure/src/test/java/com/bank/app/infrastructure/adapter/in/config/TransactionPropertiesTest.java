package com.bank.app.infrastructure.adapter.in.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionPropertiesTest {

    @Test
    void shouldApplyDefaultTimeout() {
        assertThat(new TransactionProperties(30).timeoutSeconds()).isEqualTo(30);
    }

    @Test
    void shouldRejectNonPositiveTimeout() {
        assertThatThrownBy(() -> new TransactionProperties(0))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }
}
