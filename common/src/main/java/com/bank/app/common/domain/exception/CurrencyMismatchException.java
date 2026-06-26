package com.bank.app.common.domain.exception;

public class CurrencyMismatchException extends BusinessException {
    private static final long serialVersionUID = 1L;
    public CurrencyMismatchException(String message) {
        super(message);
    }
}
