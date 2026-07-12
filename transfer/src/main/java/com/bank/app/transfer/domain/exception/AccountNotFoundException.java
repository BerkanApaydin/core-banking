package com.bank.app.transfer.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;

public class AccountNotFoundException extends BusinessException {
    private static final long serialVersionUID = 1L;

    public AccountNotFoundException(Long accountId) {
        super("error.account.not.found", new Object[]{accountId}, "Account not found. ID: " + accountId);
    }

    public AccountNotFoundException(String message) {
        super(message);
    }
}
