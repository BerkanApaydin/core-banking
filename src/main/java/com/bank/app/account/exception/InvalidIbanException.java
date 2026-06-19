package com.bank.app.account.exception;

import com.bank.app.common.exception.BusinessException;

public class InvalidIbanException extends BusinessException {
    public InvalidIbanException(String message) {
        super(message);
    }
}
