package com.bank.app.account.exception;

import com.bank.app.common.exception.BusinessException;

public class DuplicateIbanException extends BusinessException {
    public DuplicateIbanException(String message) {
        super(message);
    }
}
