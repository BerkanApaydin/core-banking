package com.bank.app.transfer.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;

public class TransferNotPendingException extends BusinessException {
    private static final long serialVersionUID = 1L;
    public TransferNotPendingException(Enum<?> currentStatus) {
        super("error.transfer_not_pending", new Object[]{currentStatus},
              "Only PENDING transfers can be completed. Current status: " + currentStatus);
    }
}
