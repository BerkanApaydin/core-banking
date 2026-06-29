package com.bank.app.audit.domain;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

public class AuditLog {
    private final Long id;
    private final String username;
    private final AuditAction action;
    private final String details;
    private final LocalDateTime timestamp;

    public AuditLog(Long id, String username, AuditAction action, String details, LocalDateTime timestamp) {
        this.id = id;
        this.username = Objects.requireNonNull(username, "Username must not be null");
        this.action = Objects.requireNonNull(action, "Action must not be null");
        this.details = Objects.requireNonNull(details, "Details must not be null");
        this.timestamp = Objects.requireNonNull(timestamp, "Timestamp must not be null");
    }

    public static AuditLog create(String username, AuditAction action, String details) {
        return create(username, action, details, Clock.systemDefaultZone());
    }

    public static AuditLog create(String username, AuditAction action, String details, Clock clock) {
        return new AuditLog(null, username, action, details, LocalDateTime.now(clock));
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getDetails() {
        return details;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
