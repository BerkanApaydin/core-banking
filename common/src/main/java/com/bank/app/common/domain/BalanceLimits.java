package com.bank.app.common.domain;

import java.math.BigDecimal;

/**
 * Single source of truth for the hard money ceiling applied to balances and
 * single transfer amounts.
 *
 * <p>The string literal is the canonical value: Jakarta's {@code @DecimalMax}
 * requires a compile-time constant, so web-layer DTOs reference
 * {@link #MAX_BALANCE} directly instead of duplicating the literal. Domain
 * code compares against {@link #MAX_BALANCE_AMOUNT}. Changing the ceiling is
 * exactly one edit.
 *
 * <p>Mirrors {@code validation.balance.max} / {@code validation.amount.max}.
 */
public final class BalanceLimits {

    /** Canonical ceiling, also used as {@code @DecimalMax#value}. */
    public static final String MAX_BALANCE = "1000000000.00";

    /** Domain-comparison form of {@link #MAX_BALANCE}. */
    public static final BigDecimal MAX_BALANCE_AMOUNT = new BigDecimal(MAX_BALANCE);

    private BalanceLimits() {}
}
