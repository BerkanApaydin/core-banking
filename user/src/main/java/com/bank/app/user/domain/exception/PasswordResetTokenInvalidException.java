package com.bank.app.user.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.BusinessFailureKind;
import com.bank.app.common.messages.MessageKeys;

public class PasswordResetTokenInvalidException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public BusinessFailureKind getFailureKind() { return BusinessFailureKind.RULE_VIOLATION; }

    @Override
    public String getErrorCode() { return "PASSWORD_RESET_TOKEN_INVALID"; }

    /**
     * Single generic message for unknown, expired and already-used tokens:
     * distinguishing them would oracle token validity to unauthenticated
     * callers. 400, not 401: the confirm endpoint is public and the UI treats
     * 401 as session expiry.
     */
    public PasswordResetTokenInvalidException() {
        super(MessageKeys.PASSWORD_RESET_TOKEN_INVALID, new Object[]{},
                "Password reset token is invalid or has expired.");
    }
}
