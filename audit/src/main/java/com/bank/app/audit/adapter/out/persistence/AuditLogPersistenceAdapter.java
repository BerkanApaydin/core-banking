package com.bank.app.audit.adapter.out.persistence;

import com.bank.app.audit.application.port.out.LoadAuditLogPort;
import com.bank.app.audit.application.port.out.SaveAuditLogPort;
import com.bank.app.audit.domain.AuditLog;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class AuditLogPersistenceAdapter implements SaveAuditLogPort, LoadAuditLogPort {

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

    @Override
    public List<AuditLog> findRecent(int limit) {
        return repository.findAllByOrderByTimestampDescIdDesc(PageRequest.of(0, Math.max(limit, 1))).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<AuditLog> findByActor(Long actorUserId, int limit) {
        return repository.findByActorUserIdOrderByTimestampDescIdDesc(
                        actorUserId, PageRequest.of(0, Math.max(limit, 1))).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
