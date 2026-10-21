package com.bank.app.user.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.BusinessFailureKind;
import com.bank.app.common.messages.MessageKeys;

/**
 * The account is locked out after too many failed MFA verification attempts
 * (TOTP code or the password step-up on the management endpoints). 429
 * (RATE_LIMITED), not 400: the request was well-formed and the caller is
 * expected to back off, not to fix the input.
 *
 * <p>Separate from {@link TooManyFailedLoginAttemptsException} so the
 * client-facing code and message describe the actual limit - the two
 * counters have different scopes (account-keyed MFA budget vs the
 * IP/username-keyed login budget) and different windows.
 */
public class TooManyFailedMfaAttemptsException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public BusinessFailureKind getFailureKind() { return BusinessFailureKind.RATE_LIMITED; }

    @Override
    public String getErrorCode() { return "TOO_MANY_FAILED_MFA_ATTEMPTS"; }

    public TooManyFailedMfaAttemptsException(String message) {
        super(MessageKeys.TOO_MANY_FAILED_MFA_ATTEMPTS, new Object[]{message},
                "Too many failed verification attempts: " + message);
    }
}
