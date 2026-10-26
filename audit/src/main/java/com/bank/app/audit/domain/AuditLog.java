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
    /**
     * Stable actor identity alongside the display snapshot {@code username}
     * (8.2, DB: V34). Nullable: history rows and anonymous/system actors have
     * no users.id. No FK by design — audit retention is independent of the
     * user lifecycle.
     */
    private final Long actorUserId;

    private AuditLog(Long id, String username, AuditAction action, String details, LocalDateTime timestamp,
                     Long actorUserId) {
        this.id = id;
        this.username = Objects.requireNonNull(username, "Username must not be null");
        this.action = Objects.requireNonNull(action, "Action must not be null");
        this.details = Objects.requireNonNull(details, "Details must not be null");
        this.timestamp = Objects.requireNonNull(timestamp, "Timestamp must not be null");
        this.actorUserId = actorUserId;
    }

    /**
     * D-2: kept for tests and tooling only. Production paths must pass an
     * explicit {@link Clock} via {@link #create(String, AuditAction, String, Clock)}
     * (injected as {@code ClockProviderPort}) so timestamps stay deterministic
     * and UTC-pinned — a hidden {@code Clock.systemUTC()} default is a purity
     * trap for future production callers.
     *
     * @deprecated test/tooling use only; production code passes a Clock.
     */
    @Deprecated(forRemoval = false)
    public static AuditLog create(String username, AuditAction action, String details) {
        return create(username, action, details, Clock.systemUTC());
    }

    public static AuditLog create(String username, AuditAction action, String details, Clock clock) {
        return create(username, action, details, null, clock);
    }

    public static AuditLog create(String username, AuditAction action, String details, Long actorUserId,
                                  Clock clock) {
        Objects.requireNonNull(clock, "Clock must not be null");
        return create(username, action, details, LocalDateTime.now(clock), actorUserId);
    }

    /**
     * Creates a new log for a publisher-supplied timestamp. Used when the
     * occurrence time comes from the event rather than a clock read here.
     */
    public static AuditLog create(String username, AuditAction action, String details,
                                  LocalDateTime timestamp, Long actorUserId) {
        return new AuditLog(null, username, action, details, timestamp, actorUserId);
    }

    /**
     * The only entry point for materializing persisted audit history.
     */
    public static AuditLog rehydrate(Long id, String username, AuditAction action, String details,
                                     LocalDateTime timestamp) {
        return rehydrate(id, username, action, details, timestamp, null);
    }

    public static AuditLog rehydrate(Long id, String username, AuditAction action, String details,
                                     LocalDateTime timestamp, Long actorUserId) {
        return new AuditLog(id, username, action, details, timestamp, actorUserId);
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

    public Long getActorUserId() {
        return actorUserId;
    }
}
