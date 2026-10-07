package com.bank.app.common.domain.exception;

public class InvalidIbanException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public String getErrorCode() { return "INVALID_IBAN"; }

    public InvalidIbanException(String message) {
        super(message);
    }
}
