package com.bank.app.account.domain;

import com.bank.app.common.domain.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.Objects;

public record AccountSuspendedEvent(
    Long accountId,
    LocalDateTime occurredAt
) implements DomainEvent {
    public AccountSuspendedEvent {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(occurredAt);
    }

    @Override
    public String aggregateType() {
        return "Account";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(accountId);
    }
}
