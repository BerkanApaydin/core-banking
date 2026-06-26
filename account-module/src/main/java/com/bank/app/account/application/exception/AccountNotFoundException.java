package com.bank.app.account.application.exception;

import com.bank.app.common.domain.exception.BusinessException;

public class AccountNotFoundException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public int getHttpStatusCode() { return 404; }

    public AccountNotFoundException(Long id) {
        super("error.account_not_found", new Object[]{id}, "Hesap bulunamadı. ID: " + id);
    }

    public AccountNotFoundException(String iban) {
        super("error.account_not_found", new Object[]{iban}, "Hesap bulunamadı. IBAN: " + iban);
    }
}
