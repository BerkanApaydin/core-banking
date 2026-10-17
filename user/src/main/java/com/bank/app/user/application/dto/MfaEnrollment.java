package com.bank.app.user.application.dto;

import java.util.Objects;

/** Authenticator enrollment material returned by {@code EnableMfaUseCase}. */
public record MfaEnrollment(
        String otpauthUri,
        String base32Secret
) {
    public MfaEnrollment {
        Objects.requireNonNull(otpauthUri, "otpauth URI must not be null");
        Objects.requireNonNull(base32Secret, "Base32 secret must not be null");
    }
}
