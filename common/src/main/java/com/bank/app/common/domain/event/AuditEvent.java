package com.bank.app.common.domain.event;

import java.time.LocalDateTime;

public record AuditEvent(String action, String details, LocalDateTime occurredAt, String username,
                         Long actorUserId) implements DomainEvent {

    public AuditEvent(String action, String details, LocalDateTime occurredAt) {
        this(action, details, occurredAt, "system", null);
    }

    public AuditEvent(String action, String details, LocalDateTime occurredAt, String username) {
        this(action, details, occurredAt, username, null);
    }

    public AuditEvent {
        if (username == null || username.isBlank()) {
            username = "system";
        }
        // actorUserId stays nullable: history rows and anonymous actors
        // (failed logins, system legs) have no stable identity (8.2).
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
