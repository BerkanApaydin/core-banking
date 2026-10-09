package com.bank.app.common.domain.event;

import java.time.LocalDateTime;

public record AuditEvent(String action, String details, LocalDateTime occurredAt, String username,
                         Long actorUserId) implements DomainEvent {

    // Typed action constants (single source for producers): the consuming
    // AuditAction enum lives in the audit BC, which common must not depend on
    // (wrong direction). AuditActionCoverageTest asserts every constant below
    // resolves via AuditAction.fromString, so an enum rename turns red instead
    // of silently breaking the audit trail.
    public static final String ACCOUNT_CREATED = "ACCOUNT_CREATED";
    public static final String ACCOUNT_DEBITED = "ACCOUNT_DEBITED";
    public static final String ACCOUNT_CREDITED = "ACCOUNT_CREDITED";
    public static final String ACCOUNT_SUSPENDED = "ACCOUNT_SUSPENDED";
    public static final String ACCOUNT_CLOSED = "ACCOUNT_CLOSED";
    public static final String TRANSFER_EXECUTED = "TRANSFER_EXECUTED";
    public static final String TRANSFER_CANCELLED = "TRANSFER_CANCELLED";
    public static final String TRANSFER_MARKED_FAILED = "TRANSFER_MARKED_FAILED";
    public static final String LOGIN_SUCCEEDED = "LOGIN_SUCCEEDED";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGOUT = "LOGOUT";
    public static final String TOKEN_REVOKED = "TOKEN_REVOKED";

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
