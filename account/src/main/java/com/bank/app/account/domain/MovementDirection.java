package com.bank.app.account.domain;

/**
 * Direction of a balance movement between two accounts.
 *
 * <p>Replaces the former {@code boolean reverse} flag on the internal move
 * path: at a call site {@code false}/{@code true} carries no meaning, and a
 * swapped flag moves money in the wrong direction. The enum keeps the intent
 * readable ({@code OUTFLOW} vs {@code REVERSAL}) and gives ledger/audit
 * branching a single typed source instead of four independent negations.
 */
public enum MovementDirection {

    /** Normal placement: debit the sender, credit the receiver. */
    OUTFLOW,

    /** Cancellation compensation: credit the sender, debit the receiver. */
    REVERSAL;

    public boolean isOutflow() {
        return this == OUTFLOW;
    }
}
