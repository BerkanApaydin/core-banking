package com.bank.app.audit.application.dto;

import com.bank.app.audit.domain.AuditLog;
import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        String username,
        Long actorUserId,
        String action,
        String details,
        LocalDateTime timestamp) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getUsername(),
                log.getActorUserId(),
                log.getAction().name(),
                log.getDetails(),
                log.getTimestamp());
    }
}
