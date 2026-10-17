package com.bank.app.user.application.port.in;

public interface ConfirmMfaUseCase {
    /**
     * Completes enrollment by verifying a TOTP code against the pending secret,
     * then enables MFA for subsequent logins.
     *
     * @throws com.bank.app.user.domain.exception.MfaNotEnrolledException when no pending secret exists
     * @throws com.bank.app.user.domain.exception.InvalidMfaCodeException when the code is wrong
     */
    void execute(Long userId, String code);
}
