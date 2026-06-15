package com.bank.app.common.exception;

public class CurrencyMismatchException extends BusinessException {
    public CurrencyMismatchException(String message) {
        super(message);
    }
}
