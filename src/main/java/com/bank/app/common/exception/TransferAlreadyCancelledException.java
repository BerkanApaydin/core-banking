package com.bank.app.common.exception;

public class TransferAlreadyCancelledException extends BusinessException {
    public TransferAlreadyCancelledException(Long id) {
        super("error.transfer_already_cancelled", new Object[]{id}, "Transfer zaten iptal edilmiş. ID: " + id);
    }
}
