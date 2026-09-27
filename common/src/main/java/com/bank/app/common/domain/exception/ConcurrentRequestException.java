package com.bank.app.common.domain.exception;

public class ConcurrentRequestException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public BusinessFailureKind getFailureKind() { return BusinessFailureKind.CONFLICT; }

    public ConcurrentRequestException(String message) {
        super(message);
    }

    public ConcurrentRequestException(String messageKey, Object[] args, String defaultMessage) {
        super(messageKey, args, defaultMessage);
    }
}
