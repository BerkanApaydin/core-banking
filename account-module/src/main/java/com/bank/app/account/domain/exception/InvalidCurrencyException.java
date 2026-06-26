package com.bank.app.account.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;

public class InvalidCurrencyException extends BusinessException {
    private static final long serialVersionUID = 1L;
    public InvalidCurrencyException(String currency) {
        super("error.invalid_currency", new Object[]{currency}, "Geçersiz para birimi: " + currency);
    }

    public InvalidCurrencyException(String messageKey, Object[] args, String defaultMessage) {
        super(messageKey, args, defaultMessage);
    }
}
