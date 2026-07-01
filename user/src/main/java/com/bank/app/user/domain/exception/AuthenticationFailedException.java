package com.bank.app.user.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;

public class AuthenticationFailedException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public int getHttpStatusCode() { return 401; }

    public AuthenticationFailedException(String message) {
        super("error.authentication_failed", new Object[]{message}, "Authentication failed: " + message);
    }

    public AuthenticationFailedException(String message, Throwable cause) {
        super("error.authentication_failed", new Object[]{message}, "Authentication failed: " + message, cause);
    }
}
