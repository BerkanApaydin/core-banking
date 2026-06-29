package com.bank.app.user.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;

public class TooManyFailedLoginAttemptsException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public int getHttpStatusCode() { return 429; }

    public TooManyFailedLoginAttemptsException(String message) {
        super("error.too_many_failed_login_attempts", new Object[]{message}, "Çok fazla başarısız giriş denemesi: " + message);
    }
}
