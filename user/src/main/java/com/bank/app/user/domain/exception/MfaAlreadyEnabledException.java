package com.bank.app.user.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.BusinessFailureKind;
import com.bank.app.common.messages.MessageKeys;

public class MfaAlreadyEnabledException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public BusinessFailureKind getFailureKind() { return BusinessFailureKind.CONFLICT; }

    @Override
    public String getErrorCode() { return "MFA_ALREADY_ENABLED"; }

    public MfaAlreadyEnabledException() {
        super(MessageKeys.MFA_ALREADY_ENABLED, new Object[]{}, "MFA is already enabled for this user.");
    }
}
