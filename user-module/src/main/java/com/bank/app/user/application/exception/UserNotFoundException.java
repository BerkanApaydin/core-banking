package com.bank.app.user.application.exception;

import com.bank.app.common.domain.exception.BusinessException;

public class UserNotFoundException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public int getHttpStatusCode() { return 404; }

    public UserNotFoundException(String message) {
        super(message);
    }
}
