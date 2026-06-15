package com.bank.app.audit.infrastructure.persistence;

import com.bank.app.audit.application.port.SaveAuditLogPort;
import com.bank.app.audit.domain.AuditLog;
import org.springframework.stereotype.Component;

@Component
public class AuditLogAdapter implements SaveAuditLogPort {

    private final SpringDataAuditLogRepository repository;

    public AuditLogAdapter(SpringDataAuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public AuditLog save(AuditLog auditLog) {
        AuditLogJpaEntity jpaEntity = new AuditLogJpaEntity(
                auditLog.getId(),
                auditLog.getUsername(),
                auditLog.getAction(),
                auditLog.getDetails(),
                auditLog.getTimestamp()
        );
        AuditLogJpaEntity savedEntity = repository.save(jpaEntity);
        return new AuditLog(
                savedEntity.getId(),
                savedEntity.getUsername(),
                savedEntity.getAction(),
                savedEntity.getDetails(),
                savedEntity.getTimestamp()
        );
    }
}
