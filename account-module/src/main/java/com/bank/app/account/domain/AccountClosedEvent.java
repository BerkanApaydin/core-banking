package com.bank.app.account.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.Objects;

public record AccountClosedEvent(
    Long accountId,
    Money finalBalance,
    LocalDateTime occurredAt
) implements DomainEvent {
    public AccountClosedEvent {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(finalBalance);
        Objects.requireNonNull(occurredAt);
    }
}
