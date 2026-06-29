package com.bank.app.transfer.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;

public class TransferAlreadyCancelledException extends BusinessException {
    private static final long serialVersionUID = 1L;
    public TransferAlreadyCancelledException(Long id) {
        super("error.transfer_already_cancelled", new Object[]{id}, "Transfer already cancelled. ID: " + id);
    }
}
