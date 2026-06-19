package com.bank.app.account.exception;

import com.bank.app.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AccountNotFoundException extends BusinessException {
    public AccountNotFoundException(String iban) {
        super("error.account_not_found_iban", new Object[]{iban}, "Hesap bulunamadı. IBAN: " + iban, HttpStatus.NOT_FOUND);
    }

    public AccountNotFoundException(Long id) {
        super("error.account_not_found_id", new Object[]{id}, "Hesap bulunamadı. ID: " + id, HttpStatus.NOT_FOUND);
    }

    @Override
    public String getErrorCode() {
        return "ACCOUNT_NOT_FOUND";
    }
}
