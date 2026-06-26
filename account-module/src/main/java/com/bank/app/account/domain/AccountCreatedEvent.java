package com.bank.app.account.domain;

import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.UserId;
import com.bank.app.common.domain.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.Objects;

public record AccountCreatedEvent(
    Long accountId,
    UserId userId,
    Iban iban,
    String ownerName,
    Money balance,
    LocalDateTime occurredAt
) implements DomainEvent {
    public AccountCreatedEvent {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(iban);
        Objects.requireNonNull(ownerName);
        Objects.requireNonNull(balance);
        Objects.requireNonNull(occurredAt);
    }
}
