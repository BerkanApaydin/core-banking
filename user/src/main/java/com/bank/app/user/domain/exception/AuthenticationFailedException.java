package com.bank.app.user.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.BusinessFailureKind;

public class AuthenticationFailedException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public BusinessFailureKind getFailureKind() { return BusinessFailureKind.AUTHENTICATION_FAILED; }

    @Override
    public String getErrorCode() { return "AUTHENTICATION_FAILED"; }

    /**
     * No framework, provider or user content ever enters {@code args} or the
     * default message template inputs: the resolved
     * {@code error.authentication_failed} template has no placeholders, so
     * i18n interpolation cannot echo anything even if a future template gains
     * one. Credential details stay in logs at the throw site, never in the
     * exception (D11/K13 — latent user-enumeration guard).
     */
    public AuthenticationFailedException() {
        super("error.authentication_failed", new Object[]{}, "Authentication failed.");
    }

    /**
     * Static, code-owned detail only (e.g. "Refresh token has expired.").
     * Never pass framework exception messages or user input here.
     */
    public AuthenticationFailedException(String staticDetail) {
        super("error.authentication_failed", new Object[]{}, "Authentication failed: " + staticDetail);
    }

    public AuthenticationFailedException(Throwable cause) {
        super("error.authentication_failed", new Object[]{}, "Authentication failed.", cause);
    }
}
