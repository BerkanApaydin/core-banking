package com.bank.app.common.domain;

/**
 * Single source of truth for free-text field lengths (mirrors
 * {@link BalanceLimits} for money).
 *
 * <p>The 255 literal was duplicated in 6 places (Account/User owner names,
 * DTO size caps). Jakarta's {@code @Size} requires compile-time constants, so
 * web-layer DTOs reference {@link #MAX_TEXT} directly; domain code compares
 * against {@link #MAX_TEXT_LENGTH}. Changing the limit is exactly one edit.
 */
public final class FieldLimits {

    /** Canonical free-text cap, also used as {@code @Size#max}. */
    public static final int MAX_TEXT_LENGTH = 255;

    /** String form for messages/DTO documentation. */
    public static final String MAX_TEXT = "255";

    private FieldLimits() {}
}
