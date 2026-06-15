package com.bank.app.common.exception;

public class TransferNotCancellableException extends BusinessException {
    public TransferNotCancellableException(String message) {
        super(message);
    }

    public TransferNotCancellableException(String messageKey, Object[] args, String defaultMessage) {
        super(messageKey, args, defaultMessage);
    }
}
