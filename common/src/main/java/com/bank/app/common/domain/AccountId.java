package com.bank.app.common.domain;

import java.util.Objects;

/**
 * Typed account identifier (same pattern as {@link UserId}).
 *
 * <p>Bare {@code Long} pairs such as {@code (senderId, receiverId)} let a
 * swapped argument order compile silently — in a money-movement path that is
 * a wrong-debit bug. Threading {@code AccountId} through the transfer
 * operation seams (creation, debit/credit, authorization) makes such swaps a
 * compile error at exactly the call sites where humans write the ids.
 * Persistence, wire DTOs and the cross-context published language keep raw
 * {@code Long}; the anti-corruption and persistence adapters translate at
 * the edge ({@code .value()} / {@code new AccountId(..)}).
 */
public record AccountId(Long value) {
    public AccountId {
        Objects.requireNonNull(value, "Account ID must not be null");
        if (value <= 0) {
            throw new IllegalArgumentException("Account ID must be positive: " + value);
        }
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
