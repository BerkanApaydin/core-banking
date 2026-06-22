package com.bank.app.common.exception;

public class InvalidIbanException extends BusinessException {
    public InvalidIbanException(String message) {
        super(message);
    }
}
