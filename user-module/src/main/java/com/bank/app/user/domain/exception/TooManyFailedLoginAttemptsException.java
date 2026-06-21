package com.bank.app.user.domain.exception;

import com.bank.app.common.exception.BusinessException;

public class TooManyFailedLoginAttemptsException extends BusinessException {
    public TooManyFailedLoginAttemptsException(String message) {
        super("error.too_many_failed_login_attempts", new Object[]{message}, "Çok fazla başarısız giriş denemesi: " + message);
    }
}
