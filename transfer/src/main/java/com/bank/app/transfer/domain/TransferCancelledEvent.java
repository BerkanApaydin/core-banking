package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.Objects;

public record TransferCancelledEvent(
    Long transferId,
    Long senderAccountId,
    Long receiverAccountId,
    Money amount,
    TransferStatus status,
    LocalDateTime occurredAt
) implements DomainEvent {
    public TransferCancelledEvent {
        Objects.requireNonNull(transferId);
        Objects.requireNonNull(senderAccountId);
        Objects.requireNonNull(receiverAccountId);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(status);
        Objects.requireNonNull(occurredAt);
    }
}
