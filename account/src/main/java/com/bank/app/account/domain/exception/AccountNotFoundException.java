package com.bank.app.account.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.BusinessFailureKind;

public class AccountNotFoundException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public BusinessFailureKind getFailureKind() { return BusinessFailureKind.NOT_FOUND; }

    public AccountNotFoundException(Long id) {
        super("error.account_not_found_id", new Object[]{id}, "Account not found. ID: " + id);
    }

    public AccountNotFoundException(String iban) {
        super("error.account_not_found_iban", new Object[]{iban}, "Account not found. IBAN: " + iban);
    }
}
