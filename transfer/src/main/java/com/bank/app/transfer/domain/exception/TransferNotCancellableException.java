package com.bank.app.transfer.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;

public class TransferNotCancellableException extends BusinessException {
    private static final long serialVersionUID = 1L;
    public TransferNotCancellableException(String messageKey, Object[] args, String defaultMessage) {
        super(messageKey, args, defaultMessage);
    }
}
