package com.bank.app.account.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;

public class AccountClosedException extends BusinessException {
    private static final long serialVersionUID = 1L;
    public AccountClosedException(String iban) {
        super("error.account_closed", new Object[]{iban}, "Account closed: " + iban);
    }
}
