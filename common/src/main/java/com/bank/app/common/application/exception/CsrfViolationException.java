package com.bank.app.common.application.exception;

/**
 * Browser CSRF validation failed. Framework-free so application services can
 * reject a forged cross-site request without depending on a web stack; the
 * inbound adapter maps it to HTTP 403.
 */
public class CsrfViolationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CsrfViolationException() {
        super("Invalid browser CSRF token.");
    }
}
