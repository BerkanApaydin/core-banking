package com.bank.app.common.domain.exception;

import java.util.Locale;

public abstract class BusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private static final Locale LOCALE = Locale.ENGLISH;
    private final String messageKey;
    private final transient Object[] args;

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

    protected BusinessException(String messageKey, Object[] args, String defaultMessage, Throwable cause) {
        super(defaultMessage, cause);
        this.messageKey = messageKey;
        this.args = args;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getArgs() {
        return args;
    }

    /**
     * HTTP status code for this exception. Defaults to 400 (Bad Request).
     */
    public int getHttpStatusCode() {
        return 400;
    }

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
