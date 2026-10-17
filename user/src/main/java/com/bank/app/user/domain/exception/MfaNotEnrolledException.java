package com.bank.app.user.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.BusinessFailureKind;
import com.bank.app.common.messages.MessageKeys;

public class MfaNotEnrolledException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public BusinessFailureKind getFailureKind() { return BusinessFailureKind.NOT_FOUND; }

    @Override
    public String getErrorCode() { return "MFA_NOT_ENROLLED"; }

    public MfaNotEnrolledException() {
        super(MessageKeys.MFA_NOT_ENROLLED, new Object[]{}, "MFA enrollment was not started for this user.");
    }
}
