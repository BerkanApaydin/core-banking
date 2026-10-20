package com.bank.app.user.application.port.in;

import com.bank.app.user.application.dto.MfaEnrollment;

public interface EnableMfaUseCase {
    /**
     * Starts MFA enrollment: generates a fresh TOTP secret, stores it encrypted
     * (MFA stays DISABLED until {@code ConfirmMfaUseCase} proves the user
     * enrolled it) and returns the authenticator enrollment material.
     *
     * @param password current password, re-checked before a secret is minted
     * @throws com.bank.app.user.domain.exception.MfaAlreadyEnabledException when MFA is already on
     * @throws com.bank.app.user.domain.exception.InvalidMfaCodeException when the password is wrong
     */
    MfaEnrollment execute(Long userId, String password);
}
