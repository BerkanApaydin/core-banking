package com.bank.app.user.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.BusinessFailureKind;
import com.bank.app.common.messages.MessageKeys;

public class TooManyFailedLoginAttemptsException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public BusinessFailureKind getFailureKind() { return BusinessFailureKind.RATE_LIMITED; }

    @Override
    public String getErrorCode() { return "TOO_MANY_FAILED_LOGIN_ATTEMPTS"; }

    public TooManyFailedLoginAttemptsException(String message) {
        super(MessageKeys.TOO_MANY_FAILED_LOGIN_ATTEMPTS, new Object[]{message}, "Too many failed login attempts: " + message);
    }
}
