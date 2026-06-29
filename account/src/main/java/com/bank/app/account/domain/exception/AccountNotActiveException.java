package com.bank.app.account.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;

public class AccountNotActiveException extends BusinessException {
    private static final long serialVersionUID = 1L;
    public AccountNotActiveException(String iban) {
        super("error.account_not_active", new Object[]{iban}, "Hesap aktif değil: " + iban);
    }
}
