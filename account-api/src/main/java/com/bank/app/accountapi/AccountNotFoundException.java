package com.bank.app.accountapi;

import com.bank.app.common.domain.exception.BusinessException;

/**
 * Not-found signal of the Account published language.
 *
 * <p>Thrown by {@link AccountApi} implementations instead of the account
 * domain's own exception so downstream contexts never depend on
 * {@code com.bank.app.account..} types — not even for error handling.
 * Message keys and HTTP status intentionally mirror the account domain ones,
 * keeping wire responses byte-identical.
 */
public class AccountNotFoundException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public int getHttpStatusCode() { return 404; }

    public AccountNotFoundException(Long id) {
        super("error.account_not_found_id", new Object[]{id}, "Account not found. ID: " + id);
    }

    public AccountNotFoundException(String iban) {
        super("error.account_not_found_iban", new Object[]{iban}, "Account not found. IBAN: " + iban);
    }
}
