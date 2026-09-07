package com.bank.app.accountapi;

import java.util.Objects;

/**
 * Read-model snapshot of an account as exposed to downstream contexts.
 * Deliberately opaque: no balance details beyond what the consumer needs,
 * no persistence or domain types leak through this boundary.
 */
public record AccountSnapshot(Long id, Long userId, String currency, String status) {
    public AccountSnapshot {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
