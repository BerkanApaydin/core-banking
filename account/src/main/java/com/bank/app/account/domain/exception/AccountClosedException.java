package com.bank.app.account.domain.exception;

import com.bank.app.common.domain.IbanLogMask;
import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.messages.MessageKeys;

public class AccountClosedException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public String getErrorCode() { return "ACCOUNT_CLOSED"; }

    public AccountClosedException(String iban) {
        super(MessageKeys.ACCOUNT_CLOSED, new Object[]{IbanLogMask.mask(iban)},
                "Account closed: " + IbanLogMask.mask(iban));
    }
}
