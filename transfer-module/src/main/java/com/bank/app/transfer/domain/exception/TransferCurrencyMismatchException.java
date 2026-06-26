package com.bank.app.transfer.domain.exception;

import com.bank.app.common.domain.exception.CurrencyMismatchException;

public class TransferCurrencyMismatchException extends CurrencyMismatchException {
    private static final long serialVersionUID = 1L;
    public TransferCurrencyMismatchException(String message) {
        super(message);
    }
}
