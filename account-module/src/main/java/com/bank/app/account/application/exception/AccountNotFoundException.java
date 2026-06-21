package com.bank.app.account.application.exception;

import com.bank.app.common.exception.BusinessException;

public class AccountNotFoundException extends BusinessException {
    public AccountNotFoundException(Long id) {
        super("error.account_not_found", new Object[]{id}, "Hesap bulunamadı. ID: " + id);
    }

    public AccountNotFoundException(String iban) {
        super("error.account_not_found", new Object[]{iban}, "Hesap bulunamadı. IBAN: " + iban);
    }
}
