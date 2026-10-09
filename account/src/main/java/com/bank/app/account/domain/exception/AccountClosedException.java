package com.bank.app.account.domain.exception;

import com.bank.app.common.domain.IbanLogMask;
import com.bank.app.common.domain.exception.BusinessException;

public class AccountClosedException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public String getErrorCode() { return "ACCOUNT_CLOSED"; }

    public AccountClosedException(String iban) {
        super("error.account_closed", new Object[]{IbanLogMask.mask(iban)},
                "Account closed: " + IbanLogMask.mask(iban));
    }
}
