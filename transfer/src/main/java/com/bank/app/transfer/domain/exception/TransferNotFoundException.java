package com.bank.app.transfer.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.BusinessFailureKind;
import com.bank.app.common.messages.MessageKeys;

public class TransferNotFoundException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public BusinessFailureKind getFailureKind() { return BusinessFailureKind.NOT_FOUND; }

    @Override
    public String getErrorCode() { return "TRANSFER_NOT_FOUND"; }

    public TransferNotFoundException(Long id) {
        super(MessageKeys.TRANSFER_NOT_FOUND, new Object[]{id}, "Transfer not found. ID: " + id);
    }
}
