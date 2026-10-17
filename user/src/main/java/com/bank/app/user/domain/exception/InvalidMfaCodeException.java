package com.bank.app.user.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.BusinessFailureKind;
import com.bank.app.common.messages.MessageKeys;

public class InvalidMfaCodeException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public BusinessFailureKind getFailureKind() { return BusinessFailureKind.RULE_VIOLATION; }

    @Override
    public String getErrorCode() { return "MFA_CODE_INVALID"; }

    /**
     * Wrong TOTP code (or wrong password on the disable path, deliberately
     * indistinguishable). 400, not 401: these endpoints run in an already
     * authenticated session, and the UI treats 401 as session expiry (it
     * would log the user out on a mere typo). The unauthenticated login path
     * keeps failing with the generic 401 instead.
     */
    public InvalidMfaCodeException() {
        super(MessageKeys.MFA_CODE_INVALID, new Object[]{}, "Invalid authenticator code.");
    }
}
