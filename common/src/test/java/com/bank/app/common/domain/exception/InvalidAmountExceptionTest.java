package com.bank.app.common.domain.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvalidAmountExceptionTest {

    @Test
    void shouldExposeStableErrorCodeAndFailureKind() {
        InvalidAmountException exception = new InvalidAmountException("Amount must not be zero");

        assertThat(exception.getErrorCode()).isEqualTo("INVALID_AMOUNT");
        assertThat(exception.getFailureKind()).isEqualTo(BusinessFailureKind.RULE_VIOLATION);
        assertThat(exception).hasMessage("Amount must not be zero");
    }
}
