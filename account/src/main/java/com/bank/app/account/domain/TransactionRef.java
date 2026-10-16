package com.bank.app.account.domain;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

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

    /**
     * Test/tooling use only. Production paths must pass an explicit ID supplier
     * via {@link #newRef(Supplier)} (injected as {@code IdGeneratorPort}) so refs
     * stay deterministic and seedable — a hidden {@code UUID.randomUUID()} default
     * is a purity trap for future production callers.
     *
     * @deprecated test/tooling use only; production code passes an ID supplier.
     */
    @Deprecated(forRemoval = false)
    public static TransactionRef newRef() {
        return new TransactionRef(UUID.randomUUID().toString());
    }

    /**
     * D-1: testable overload — production callers pass
     * {@code IdGeneratorPort::newId} so tests can seed exact refs instead of
     * racing random UUIDs. The no-arg variant stays for non-production paths
     * (tests, tooling).
     */
    public static TransactionRef newRef(Supplier<String> idSupplier) {
        Objects.requireNonNull(idSupplier, "ID supplier must not be null");
        return new TransactionRef(Objects.requireNonNull(idSupplier.get(), "Generated ref must not be null"));
    }

    @Override
    public String toString() {
        return value;
    }
}
