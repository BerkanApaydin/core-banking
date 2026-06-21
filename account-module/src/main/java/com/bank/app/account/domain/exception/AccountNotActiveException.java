package com.bank.app.account.domain.exception;

import com.bank.app.common.exception.BusinessException;

public class AccountNotActiveException extends BusinessException {
    public AccountNotActiveException(String message) {
        super(message);
    }
}
