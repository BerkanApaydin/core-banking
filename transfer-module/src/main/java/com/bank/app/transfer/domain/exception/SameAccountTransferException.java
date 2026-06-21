package com.bank.app.transfer.domain.exception;

import com.bank.app.common.exception.BusinessException;

public class SameAccountTransferException extends BusinessException {
    public SameAccountTransferException(String iban) {
        super("error.same_account_transfer", new Object[]{iban}, "Gönderici ve alıcı IBAN aynı olamaz: " + iban);
    }
}
