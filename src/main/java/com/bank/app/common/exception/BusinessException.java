package com.bank.app.common.exception;

import org.springframework.http.HttpStatus;

public abstract class BusinessException extends RuntimeException {
    private final String messageKey;
    private final Object[] args;
    private final HttpStatus httpStatus;

    protected BusinessException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    protected BusinessException(String message, HttpStatus httpStatus) {
        super(message);
        this.messageKey = null;
        this.args = null;
        this.httpStatus = httpStatus;
    }

    protected BusinessException(String messageKey, Object[] args, String defaultMessage) {
        this(messageKey, args, defaultMessage, HttpStatus.BAD_REQUEST);
    }

    protected BusinessException(String messageKey, Object[] args, String defaultMessage, HttpStatus httpStatus) {
        super(defaultMessage);
        this.messageKey = messageKey;
        this.args = args;
        this.httpStatus = httpStatus;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getArgs() {
        return args;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
