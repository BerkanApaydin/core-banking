package com.bank.app.account.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Double-entry operation reference: the two journal legs (debit + credit) of
 * one business operation share a single ref, and the ledger invariant
 * (legs of a ref net to zero, see V29) is keyed on it.
 *
 * <p>Typed instead of a bare {@code String} (S6): call sites cannot swap it
 * with an IBAN, username or idempotency key by accident. The 36-char cap
 * mirrors the {@code ledger_entries.transaction_ref VARCHAR(36)} column, so a
 * too-long ref fails in the domain instead of at flush time.
 */
public record TransactionRef(String value) {

    public TransactionRef {
        Objects.requireNonNull(value, "Transaction ref must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Transaction ref must not be blank");
        }
        if (value.length() > 36) {
            throw new IllegalArgumentException(
                    "Transaction ref must be at most 36 characters: " + value);
        }
    }

    /** Groups the legs of one business operation (debit + credit share it). */
    public static TransactionRef newRef() {
        return new TransactionRef(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
