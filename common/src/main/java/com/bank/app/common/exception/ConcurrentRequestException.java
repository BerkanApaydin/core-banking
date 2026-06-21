package com.bank.app.common.exception;

public class ConcurrentRequestException extends BusinessException {
    public ConcurrentRequestException(String message) {
        super(message);
    }

    public ConcurrentRequestException(String messageKey, Object[] args, String defaultMessage) {
        super(messageKey, args, defaultMessage);
    }
}
