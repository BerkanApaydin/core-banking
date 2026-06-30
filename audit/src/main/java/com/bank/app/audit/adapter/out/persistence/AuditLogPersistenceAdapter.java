package com.bank.app.audit.adapter.out.persistence;

import com.bank.app.audit.application.port.out.SaveAuditLogPort;
import com.bank.app.audit.domain.AuditLog;
import org.springframework.stereotype.Component;

@Component
public class AuditLogPersistenceAdapter implements SaveAuditLogPort {

    private final AuditLogJpaRepository repository;
    private final AuditLogJpaMapper mapper;

    public AuditLogPersistenceAdapter(AuditLogJpaRepository repository, AuditLogJpaMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public AuditLog save(AuditLog auditLog) {
        AuditLogJpaEntity entity = mapper.toJpaEntity(auditLog);
        AuditLogJpaEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }
}
