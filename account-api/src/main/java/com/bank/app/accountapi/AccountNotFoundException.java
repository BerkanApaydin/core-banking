package com.bank.app.accountapi;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.BusinessFailureKind;

/**
 * Not-found signal of the Account published language.
 *
 * <p>Thrown by {@link AccountApi} implementations instead of the account
 * domain's own exception so downstream contexts never depend on
 * {@code com.bank.app.account..} types — not even for error handling.
 * Message keys and failure kind intentionally mirror the account domain ones,
 * preserving the public failure contract across the context boundary.
 */
public class AccountNotFoundException extends BusinessException {
    private static final long serialVersionUID = 1L;

    // Deliberately no getErrorCode override: the code is instance-variant by
    // design (ACCOUNT_NOT_FOUND_ID vs ACCOUNT_NOT_FOUND_IBAN), mirroring the
    // account domain contract across the context boundary.

    @Override
    public BusinessFailureKind getFailureKind() { return BusinessFailureKind.NOT_FOUND; }

    public AccountNotFoundException(Long id) {
        super("error.account_not_found_id", new Object[]{id}, "Account not found. ID: " + id);
    }

    public AccountNotFoundException(String iban) {
        super("error.account_not_found_iban", new Object[]{iban}, "Account not found. IBAN: " + iban);
    }
}
