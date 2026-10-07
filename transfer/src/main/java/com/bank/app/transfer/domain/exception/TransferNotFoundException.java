package com.bank.app.transfer.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.BusinessFailureKind;

public class TransferNotFoundException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public BusinessFailureKind getFailureKind() { return BusinessFailureKind.NOT_FOUND; }

    @Override
    public String getErrorCode() { return "TRANSFER_NOT_FOUND"; }

    public TransferNotFoundException(Long id) {
        super("error.transfer_not_found", new Object[]{id}, "Transfer not found. ID: " + id);
    }
}
