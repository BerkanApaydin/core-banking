package com.bank.app.user.domain;

import com.bank.app.common.domain.event.DomainEvent;
import java.time.LocalDateTime;

public record UserRegisteredEvent(
        String userId,
        String username,
        String role,
        LocalDateTime occurredAt) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "User";
    }

    @Override
    public String aggregateId() {
        return userId != null ? userId : "unknown";
    }
}
