package com.bank.app.user.exception;

import com.bank.app.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class TooManyFailedLoginAttemptsException extends BusinessException {
    private static final long serialVersionUID = 1L;

    public TooManyFailedLoginAttemptsException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS);
    }
}
