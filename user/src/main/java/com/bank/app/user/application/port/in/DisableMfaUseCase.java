package com.bank.app.user.application.port.in;

public interface DisableMfaUseCase {
    /**
     * Removes the second factor after re-authenticating (password) and
     * verifying a current TOTP code. The encrypted secret is deleted with it.
     *
     * @throws com.bank.app.user.domain.exception.MfaNotEnrolledException when MFA is not enabled
     * @throws com.bank.app.user.domain.exception.InvalidMfaCodeException when password or code is wrong
     */
    void execute(Long userId, String password, String code);
}
