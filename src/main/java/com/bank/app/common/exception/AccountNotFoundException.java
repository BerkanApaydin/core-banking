package com.bank.app.common.exception;

public class AccountNotFoundException extends BusinessException {
    public AccountNotFoundException(String iban) {
        super("error.account_not_found_iban", new Object[]{iban}, "Hesap bulunamadı. IBAN: " + iban);
    }

    public AccountNotFoundException(Long id) {
        super("error.account_not_found_id", new Object[]{id}, "Hesap bulunamadı. ID: " + id);
    }
}
