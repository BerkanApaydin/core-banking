package com.bank.app.accountapi;

import com.bank.app.common.domain.Money;

import java.util.Objects;

/**
 * Opaque result of a balance mutation performed by the Account context.
 * Carries the post-transaction balances for observability only — domain events
 * stay inside the Account context (it publishes them itself) and never cross
 * the context boundary.
 */
public record AccountAdjustmentResult(
        Long senderAccountId,
        Long receiverAccountId,
        Money senderNewBalance,
        Money receiverNewBalance) {
    public AccountAdjustmentResult {
        Objects.requireNonNull(senderAccountId, "senderAccountId must not be null");
        Objects.requireNonNull(receiverAccountId, "receiverAccountId must not be null");
        Objects.requireNonNull(senderNewBalance, "senderNewBalance must not be null");
        Objects.requireNonNull(receiverNewBalance, "receiverNewBalance must not be null");
    }
}
