package com.bank.app.common.domain.event;

import java.time.LocalDateTime;

public record AuditEvent(String action, String details, LocalDateTime occurredAt, String username) implements DomainEvent {

    public AuditEvent(String action, String details, LocalDateTime occurredAt) {
        this(action, details, occurredAt, "system");
    }

    public AuditEvent {
        if (username == null || username.isBlank()) {
            username = "system";
        }
    }

    @Override
    public String aggregateType() {
        return "Audit";
    }

    @Override
    public String aggregateId() {
        return "system";
    }
}
