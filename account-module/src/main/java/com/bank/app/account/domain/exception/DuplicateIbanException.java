package com.bank.app.account.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;

public class DuplicateIbanException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public int getHttpStatusCode() { return 409; }

    public DuplicateIbanException(String iban) {
        super("error.duplicate_iban", new Object[]{iban}, "An account already exists with this IBAN: " + iban);
    }

    public DuplicateIbanException(String messageKey, Object[] args, String defaultMessage) {
        super(messageKey, args, defaultMessage);
    }
}
