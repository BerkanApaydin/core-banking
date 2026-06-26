package com.bank.app.common.domain.exception;

public class InvalidIbanException extends BusinessException {
    private static final long serialVersionUID = 1L;
    public InvalidIbanException(String message) {
        super(message);
    }
}
