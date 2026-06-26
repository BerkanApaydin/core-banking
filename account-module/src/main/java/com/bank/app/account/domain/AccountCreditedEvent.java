package com.bank.app.account.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.Objects;

public record AccountCreditedEvent(
    Long accountId,
    Money amount,
    Money newBalance,
    LocalDateTime occurredAt
) implements DomainEvent {
    public AccountCreditedEvent {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(newBalance);
        Objects.requireNonNull(occurredAt);
    }
}
