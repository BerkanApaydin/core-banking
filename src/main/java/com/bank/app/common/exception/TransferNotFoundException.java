package com.bank.app.common.exception;

public class TransferNotFoundException extends BusinessException {
    public TransferNotFoundException(Long id) {
        super("error.transfer_not_found", new Object[]{id}, "Transfer bulunamadı. ID: " + id);
    }
}
