package com.bank.app.user.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.BusinessFailureKind;

public class UserNotFoundException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public BusinessFailureKind getFailureKind() { return BusinessFailureKind.NOT_FOUND; }

    @Override
    public String getErrorCode() { return "USER_NOT_FOUND"; }

    public UserNotFoundException(String message) {
        super(message);
    }
}
