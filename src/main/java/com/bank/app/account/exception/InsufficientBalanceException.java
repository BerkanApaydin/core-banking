package com.bank.app.account.exception;

import com.bank.app.common.exception.BusinessException;

public class InsufficientBalanceException extends BusinessException {
    public InsufficientBalanceException(String message) {
        super(message);
    }

    public InsufficientBalanceException(String messageKey, Object[] args, String defaultMessage) {
        super(messageKey, args, defaultMessage);
    }
}
