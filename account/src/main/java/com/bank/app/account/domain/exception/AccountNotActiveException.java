package com.bank.app.account.domain.exception;

import com.bank.app.common.domain.IbanLogMask;
import com.bank.app.common.domain.exception.BusinessException;

public class AccountNotActiveException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public String getErrorCode() { return "ACCOUNT_NOT_ACTIVE"; }

    public AccountNotActiveException(String iban) {
        super("error.account_not_active", new Object[]{IbanLogMask.mask(iban)},
                "Account not active: " + IbanLogMask.mask(iban));
    }
}
