package com.bank.app.user.application.port.out;

/** An authentication-related database or security provider failed before credentials could be verified. */
public final class AuthenticationBackendUnavailableException extends RuntimeException {

    public AuthenticationBackendUnavailableException(Throwable cause) {
        super("Authentication backend is unavailable", cause);
    }
}
