package com.bank.app.user.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.BusinessFailureKind;

public class AuthenticationFailedException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public BusinessFailureKind getFailureKind() { return BusinessFailureKind.AUTHENTICATION_FAILED; }

    public AuthenticationFailedException(String message) {
        super("error.authentication_failed", new Object[]{message}, "Authentication failed: " + message);
    }

    public AuthenticationFailedException(String message, Throwable cause) {
        super("error.authentication_failed", new Object[]{message}, "Authentication failed: " + message, cause);
    }
}
