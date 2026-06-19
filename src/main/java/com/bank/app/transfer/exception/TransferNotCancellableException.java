package com.bank.app.transfer.exception;

import com.bank.app.common.exception.BusinessException;

public class TransferNotCancellableException extends BusinessException {
    public TransferNotCancellableException(String message) {
        super(message);
    }

    public TransferNotCancellableException(String messageKey, Object[] args, String defaultMessage) {
        super(messageKey, args, defaultMessage);
    }
}
