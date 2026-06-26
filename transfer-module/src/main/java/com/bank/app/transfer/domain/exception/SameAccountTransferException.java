package com.bank.app.transfer.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;

public class SameAccountTransferException extends BusinessException {
    private static final long serialVersionUID = 1L;
    public SameAccountTransferException(String iban) {
        super("error.same_account_transfer", new Object[]{iban}, "Aynı hesaba transfer yapılamaz: " + iban);
    }
}
