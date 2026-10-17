package com.bank.app.user.application.port.in;

public interface ConfirmPasswordResetUseCase {
    /**
     * Completes a password reset: consumes the single-use token and sets the
     * new password (which retires every outstanding session via the token
     * version bump in {@code User.changePassword}).
     *
     * @throws com.bank.app.user.domain.exception.PasswordResetTokenInvalidException for unknown/expired/used tokens
     * @throws com.bank.app.user.domain.exception.WeakPasswordException when the new password violates policy
     */
    void execute(String token, String newPassword);
}
