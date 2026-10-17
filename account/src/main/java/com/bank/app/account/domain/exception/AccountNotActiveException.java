package com.bank.app.account.domain.exception;

import com.bank.app.common.domain.IbanLogMask;
import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.messages.MessageKeys;

public class AccountNotActiveException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public String getErrorCode() { return "ACCOUNT_NOT_ACTIVE"; }

    public AccountNotActiveException(String iban) {
        super(MessageKeys.ACCOUNT_NOT_ACTIVE, new Object[]{IbanLogMask.mask(iban)},
                "Account not active: " + IbanLogMask.mask(iban));
    }
}
