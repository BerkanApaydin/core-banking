package com.bank.app.common.exception;

public abstract class BusinessException extends RuntimeException {
    private final String messageKey;
    private final Object[] args;

    protected BusinessException(String message) {
        super(message);
        this.messageKey = null;
        this.args = null;
    }

    protected BusinessException(String messageKey, Object[] args, String defaultMessage) {
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
