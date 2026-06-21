package com.bank.app.common.exception;

public class AuthorizationException extends RuntimeException {
    private final String messageKey;
    private final Object[] args;

    public AuthorizationException(String message) {
        super(message);
        this.messageKey = null;
        this.args = null;
    }

    public AuthorizationException(String messageKey, Object[] args, String defaultMessage) {
        super(defaultMessage);
        this.messageKey = messageKey;
        this.args = args;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getArgs() {
        return args;
    }
}
