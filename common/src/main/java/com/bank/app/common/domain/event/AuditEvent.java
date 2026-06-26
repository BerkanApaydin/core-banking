package com.bank.app.common.domain.event;

import java.time.LocalDateTime;

public record AuditEvent(String action, String details, LocalDateTime occurredAt) implements DomainEvent {
}
