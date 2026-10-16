package com.bank.app.common.domain.exception;

/**
 * D-4: typed zero-amount rejection.
 *
 * <p>Zero-amount guards previously threw a bare {@link IllegalArgumentException}
 * while every other domain invariant carries a typed, error-coded
 * {@link BusinessException}. This closes the gap: same 400 mapping (via
 * {@link BusinessFailureKind#RULE_VIOLATION}), but clients and logs now see a
 * stable {@code INVALID_AMOUNT} code instead of a generic 400.
 */
public class InvalidAmountException extends BusinessException {
    private static final long serialVersionUID = 1L;

    @Override
    public String getErrorCode() { return "INVALID_AMOUNT"; }

    public InvalidAmountException(String message) {
        super("error.invalid_amount", null, message);
    }
}
