package com.bank.app.account.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.messages.MessageKeys;

public class DistinctAccountsRequiredException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public String getErrorCode() { return "DISTINCT_ACCOUNTS_REQUIRED"; }

    public DistinctAccountsRequiredException(Long accountId) {
        super(MessageKeys.DISTINCT_ACCOUNTS_REQUIRED, new Object[]{accountId},
                "Sender and receiver accounts must be different: " + accountId);
    }
}
