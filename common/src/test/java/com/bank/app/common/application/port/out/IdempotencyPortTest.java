package com.bank.app.common.application.port.out;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IdempotencyPort")
class IdempotencyPortTest {

    @Nested
    @DisplayName("SaveResult")
    class SaveResult {

        @Test
        @DisplayName("should create with created true")
        void shouldCreateWithCreatedTrue() {
            IdempotencyPort.SaveResult result = new IdempotencyPort.SaveResult(true);
            assertThat(result.created()).isTrue();
        }

        @Test
        @DisplayName("should create with created false")
        void shouldCreateWithCreatedFalse() {
            IdempotencyPort.SaveResult result = new IdempotencyPort.SaveResult(false);
            assertThat(result.created()).isFalse();
        }
    }
}
