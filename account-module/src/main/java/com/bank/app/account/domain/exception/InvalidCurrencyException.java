package com.bank.app.account.domain.exception;

import com.bank.app.common.exception.BusinessException;

public class InvalidCurrencyException extends BusinessException {
    public InvalidCurrencyException(String message) {
        super(message);
    }
}
