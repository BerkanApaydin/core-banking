package com.bank.app.common.exception;

public class DuplicateIbanException extends BusinessException {
    public DuplicateIbanException(String message) {
        super(message);
    }
}
