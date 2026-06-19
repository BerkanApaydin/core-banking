package com.bank.app.transfer.exception;

import com.bank.app.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class TransferNotFoundException extends BusinessException {
    public TransferNotFoundException(Long id) {
        super("error.transfer_not_found", new Object[]{id}, "Transfer bulunamadı. ID: " + id, HttpStatus.NOT_FOUND);
    }
}
