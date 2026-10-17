package com.bank.app.transfer.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.messages.MessageKeys;

public class TransferAlreadyCancelledException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public String getErrorCode() { return "TRANSFER_ALREADY_CANCELLED"; }

    public TransferAlreadyCancelledException(Long id) {
        super(MessageKeys.TRANSFER_ALREADY_CANCELLED, new Object[]{id}, "Transfer already cancelled. ID: " + id);
    }
}
