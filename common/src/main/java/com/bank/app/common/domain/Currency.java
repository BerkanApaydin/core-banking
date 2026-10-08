package com.bank.app.common.domain;

import com.bank.app.common.domain.exception.CurrencyMismatchException;

public enum Currency {
    TRY, USD, EUR;

    /**
     * Parses a currency code from persistence snapshots or API input.
     * Replaces bare {@code Currency.valueOf} at trust boundaries so an
     * unknown code surfaces as a domain failure (400) instead of a generic
     * {@code IllegalArgumentException}.
     */
    public static Currency fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new CurrencyMismatchException("Currency must not be empty");
        }
        try {
            return Currency.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException unknown) {
            throw new CurrencyMismatchException("Unsupported currency: " + code);
        }
    }
}
