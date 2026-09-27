package com.bank.app.user.application.port.out;

/**
 * Raised when the failed-login state cannot be read or written. Login must
 * fail closed instead of issuing a token without enforced attempt limits.
 */
public final class LoginAttemptStoreUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LoginAttemptStoreUnavailableException(Throwable cause) {
        super("Failed-login state is unavailable", cause);
    }
}
