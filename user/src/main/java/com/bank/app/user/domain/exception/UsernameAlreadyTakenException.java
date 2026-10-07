package com.bank.app.user.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.BusinessFailureKind;

public class UsernameAlreadyTakenException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public BusinessFailureKind getFailureKind() { return BusinessFailureKind.CONFLICT; }

    @Override
    public String getErrorCode() { return "USERNAME_TAKEN"; }

    public UsernameAlreadyTakenException(String username) {
        super("error.username_taken", new Object[]{username}, "Username is already in use: " + username);
    }
}
