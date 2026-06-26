package com.bank.app.transfer.application.exception;

import com.bank.app.common.domain.exception.BusinessException;

public class TransferNotFoundException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public int getHttpStatusCode() { return 404; }

    public TransferNotFoundException(Long id) {
        super("error.transfer_not_found", new Object[]{id}, "Transfer bulunamadı. ID: " + id);
    }
}
