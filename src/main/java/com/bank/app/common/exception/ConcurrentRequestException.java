package com.bank.app.common.exception;

import org.springframework.http.HttpStatus;

public class ConcurrentRequestException extends BusinessException {
    public ConcurrentRequestException(String message) {
        super(message, HttpStatus.CONFLICT);
    }

    public ConcurrentRequestException(String messageKey, Object[] args, String defaultMessage) {
        super(messageKey, args, defaultMessage, HttpStatus.CONFLICT);
    }
}
