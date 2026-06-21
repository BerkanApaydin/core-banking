package com.bank.app.account.domain.exception;

import com.bank.app.common.exception.BusinessException;

public class AccountNotActiveException extends BusinessException {
    public AccountNotActiveException(String iban) {
        super("error.account_not_active", new Object[]{iban}, "Hesap aktif değil: " + iban);
    }
}
