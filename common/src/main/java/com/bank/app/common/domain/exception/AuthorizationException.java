package com.bank.app.common.domain.exception;

public class AuthorizationException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public BusinessFailureKind getFailureKind() { return BusinessFailureKind.ACCESS_DENIED; }

    public AuthorizationException(String message) {
        super(message);
    }

    public AuthorizationException(String messageKey, Object[] args, String defaultMessage) {
        super(messageKey, args, defaultMessage);
    }
}
