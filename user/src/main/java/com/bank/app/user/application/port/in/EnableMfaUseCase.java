package com.bank.app.user.application.port.in;

import com.bank.app.user.application.dto.MfaEnrollment;

public interface EnableMfaUseCase {
    /**
     * Starts MFA enrollment: generates a fresh TOTP secret, stores it encrypted
     * (MFA stays DISABLED until {@code ConfirmMfaUseCase} proves the user
     * enrolled it) and returns the authenticator enrollment material.
     *
     * @throws com.bank.app.user.domain.exception.MfaAlreadyEnabledException when MFA is already on
     */
    MfaEnrollment execute(Long userId);
}
