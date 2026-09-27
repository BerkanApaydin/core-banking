package com.bank.app.user.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.BusinessFailureKind;

public class TooManyFailedLoginAttemptsException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public BusinessFailureKind getFailureKind() { return BusinessFailureKind.RATE_LIMITED; }

    public TooManyFailedLoginAttemptsException(String message) {
        super("error.too_many_failed_login_attempts", new Object[]{message}, "Too many failed login attempts: " + message);
    }
}
