package com.bank.app.common.domain.exception;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void codeShouldBeAStableIdentifierWithoutTransportMapping(ErrorCode code) {
        assertThat(code.code()).isEqualTo(code.name());
    }
}
