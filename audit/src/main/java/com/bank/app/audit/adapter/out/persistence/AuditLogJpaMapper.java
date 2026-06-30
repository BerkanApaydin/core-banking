package com.bank.app.audit.adapter.out.persistence;

import com.bank.app.audit.domain.AuditAction;
import com.bank.app.audit.domain.AuditLog;
import org.springframework.stereotype.Component;

@Component
public class AuditLogJpaMapper {

    public AuditLogJpaEntity toJpaEntity(AuditLog auditLog) {
        if (auditLog == null) {
            throw new IllegalArgumentException("AuditLog must not be null");
        }
        return new AuditLogJpaEntity(
                auditLog.getId(),
                auditLog.getUsername(),
                auditLog.getAction().name(),
                auditLog.getDetails(),
                auditLog.getTimestamp()
        );
    }

    public AuditLog toDomain(AuditLogJpaEntity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity must not be null");
        }
        return new AuditLog(
                entity.getId(),
                entity.getUsername(),
                AuditAction.valueOf(entity.getAction()),
                entity.getDetails(),
                entity.getTimestamp()
        );
    }
}
