package com.bank.app.transfer.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;

public class TransferNotPendingException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public String getErrorCode() { return "TRANSFER_NOT_PENDING"; }

    public TransferNotPendingException(Enum<?> currentStatus) {
        this(currentStatus, "completed");
    }

    public TransferNotPendingException(Enum<?> currentStatus, String operation) {
        super("error.transfer_not_pending", new Object[]{currentStatus},
              "Only PENDING transfers can be " + operation + ". Current status: " + currentStatus);
    }
}
