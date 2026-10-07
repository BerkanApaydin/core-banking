package com.bank.app.account.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;

public class AccountBalanceLimitExceededException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public String getErrorCode() { return "ACCOUNT_BALANCE_LIMIT_EXCEEDED"; }

    public AccountBalanceLimitExceededException(String messageKey, Object[] args, String defaultMessage) {
        super(messageKey, args, defaultMessage);
    }
}
