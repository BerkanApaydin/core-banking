package com.bank.app.user.application.port.out;

/**
 * Revocation state cannot be checked or persisted. An authenticated request
 * must fail closed because another instance may have revoked the token.
 */
public final class RevocationStoreUnavailableException extends RuntimeException {

    public RevocationStoreUnavailableException(Throwable cause) {
        super("Token revocation state is unavailable", cause);
    }
}
