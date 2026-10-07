package com.bank.app.common.domain.exception;

public class CurrencyMismatchException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public String getErrorCode() { return "CURRENCY_MISMATCH"; }

    public CurrencyMismatchException(String message) {
        super(message);
    }
}
