package com.bank.app.account.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.messages.MessageKeys;

public class InvalidCurrencyException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public String getErrorCode() { return "INVALID_CURRENCY"; }

    public InvalidCurrencyException(String currency) {
        super(MessageKeys.INVALID_CURRENCY, new Object[]{currency}, "Invalid currency: " + currency);
    }

    public InvalidCurrencyException(String messageKey, Object[] args, String defaultMessage) {
        super(messageKey, args, defaultMessage);
    }
}
