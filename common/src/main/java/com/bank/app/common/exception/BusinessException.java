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

    private static final java.util.Locale LOCALE = java.util.Locale.ENGLISH;

    public String getErrorCode() {
        if (messageKey != null) {
            return messageKey.replace("error.", "").toUpperCase(LOCALE);
        }
        String simpleName = getClass().getSimpleName();
        return simpleName
                .replaceAll("Exception$", "")
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .toUpperCase(LOCALE);
    }
}
