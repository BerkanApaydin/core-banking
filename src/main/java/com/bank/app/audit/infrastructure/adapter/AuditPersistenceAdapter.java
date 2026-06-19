package com.bank.app.audit.infrastructure.adapter;

import com.bank.app.audit.application.port.out.SaveAuditLogPort;
import com.bank.app.audit.domain.AuditLog;
import com.bank.app.audit.infrastructure.persistence.AuditLogJpaEntity;
import com.bank.app.audit.infrastructure.persistence.AuditLogJpaRepository;
import org.springframework.stereotype.Component;

@Component
public class AuditPersistenceAdapter implements SaveAuditLogPort {

    private final AuditLogJpaRepository repository;

    public AuditPersistenceAdapter(AuditLogJpaRepository repository) {
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
