package com.bank.app.common.domain.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    void codeShouldReturnEnumName() {
        assertThat(ErrorCode.GENERAL_INTERNAL_ERROR.code()).isEqualTo("GENERAL_INTERNAL_ERROR");
    }

    @Test
    void validationFailedShouldHave400() {
        assertThat(ErrorCode.VALIDATION_FAILED.getHttpStatus()).isEqualTo(400);
    }

    @Test
    void authenticationFailedShouldHave401() {
        assertThat(ErrorCode.AUTHENTICATION_FAILED.getHttpStatus()).isEqualTo(401);
    }

    @Test
    void accessDeniedShouldHave403() {
        assertThat(ErrorCode.ACCESS_DENIED.getHttpStatus()).isEqualTo(403);
    }

    @Test
    void resourceNotFoundShouldHave404() {
        assertThat(ErrorCode.RESOURCE_NOT_FOUND.getHttpStatus()).isEqualTo(404);
    }

    @Test
    void concurrentRequestShouldHave409() {
        assertThat(ErrorCode.CONCURRENT_REQUEST.getHttpStatus()).isEqualTo(409);
    }

    @Test
    void generalInternalErrorShouldHave500() {
        assertThat(ErrorCode.GENERAL_INTERNAL_ERROR.getHttpStatus()).isEqualTo(500);
    }
}
