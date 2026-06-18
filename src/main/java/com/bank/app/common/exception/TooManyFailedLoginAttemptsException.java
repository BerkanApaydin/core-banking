package com.bank.app.common.exception;

public class TooManyFailedLoginAttemptsException extends RuntimeException {
    public TooManyFailedLoginAttemptsException(String message) {
        super(message);
    }
}
