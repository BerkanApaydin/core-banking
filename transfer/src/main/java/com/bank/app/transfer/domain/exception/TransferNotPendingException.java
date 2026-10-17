package com.bank.app.transfer.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.messages.MessageKeys;

public class TransferNotPendingException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public String getErrorCode() { return "TRANSFER_NOT_PENDING"; }

    public TransferNotPendingException(Enum<?> currentStatus) {
        this(currentStatus, "completed");
    }

    public TransferNotPendingException(Enum<?> currentStatus, String operation) {
        super(MessageKeys.TRANSFER_NOT_PENDING, new Object[]{currentStatus},
              "Only PENDING transfers can be " + operation + ". Current status: " + currentStatus);
    }
}
