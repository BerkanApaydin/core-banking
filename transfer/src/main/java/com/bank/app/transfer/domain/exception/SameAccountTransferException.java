package com.bank.app.transfer.domain.exception;

import com.bank.app.common.domain.IbanLogMask;
import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.messages.MessageKeys;

public class SameAccountTransferException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public String getErrorCode() { return "SAME_ACCOUNT_TRANSFER"; }

    public SameAccountTransferException(String iban) {
        super(MessageKeys.SAME_ACCOUNT_TRANSFER, new Object[]{IbanLogMask.mask(iban)},
                "Cannot transfer to the same account: " + IbanLogMask.mask(iban));
    }
}
