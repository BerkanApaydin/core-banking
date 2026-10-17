package com.bank.app.account.domain.exception;

import com.bank.app.common.domain.IbanLogMask;
import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.BusinessFailureKind;
import com.bank.app.common.messages.MessageKeys;

public class AccountNotFoundException extends BusinessException {
    private static final long serialVersionUID = 1L;

    // Deliberately no getErrorCode override: the code is instance-variant by
    // design (ACCOUNT_NOT_FOUND_ID vs ACCOUNT_NOT_FOUND_IBAN) — a per-class
    // literal would collapse two distinct client-visible failures.

    @Override
    public BusinessFailureKind getFailureKind() { return BusinessFailureKind.NOT_FOUND; }

    public AccountNotFoundException(Long id) {
        super(MessageKeys.ACCOUNT_NOT_FOUND_ID, new Object[]{id}, "Account not found. ID: " + id);
    }

    public AccountNotFoundException(String iban) {
        super(MessageKeys.ACCOUNT_NOT_FOUND_IBAN, new Object[]{IbanLogMask.mask(iban)},
                "Account not found. IBAN: " + IbanLogMask.mask(iban));
    }
}
