package com.bank.app.audit.adapter.out.persistence;

import com.bank.app.audit.application.port.out.LoadAuditLogPort;
import com.bank.app.audit.application.port.out.SaveAuditLogPort;
import com.bank.app.audit.application.port.out.AuditRetentionPort;
import com.bank.app.audit.domain.AuditLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class AuditLogPersistenceAdapter implements SaveAuditLogPort, LoadAuditLogPort, AuditRetentionPort {

    /**
     * Defense-in-depth page cap. The application layer
     * ({@code GetAuditLogsQueryImpl}) already caps via {@code AuditProperties},
     * but the adapter must never execute an unbounded scan even if a future
     * caller bypasses that guard.
     */
    static final int MAX_PAGE_SIZE = 500;

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
        return repository.findAllByOrderByTimestampDescIdDesc(PageRequest.of(0, cap(limit))).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<AuditLog> findByActor(Long actorUserId, int limit) {
        return repository.findByActorUserIdOrderByTimestampDescIdDesc(
                        actorUserId, PageRequest.of(0, cap(limit))).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<AuditLog> findByTimeRange(LocalDateTime from, LocalDateTime to, int limit) {
        return repository.findByTimestampBetweenOrderByTimestampDescIdDesc(
                        from, to, PageRequest.of(0, cap(limit))).stream()
                .map(mapper::toDomain)
                .toList();
    }

    /** Rows per retention batch; small enough to stay far under the 30s scheduler-lock budget. */
    static final int RETENTION_BATCH_SIZE = 1000;
    /** Batches per schedule: a 10k-row ceiling per run; larger backlogs drain over consecutive schedules. */
    static final int MAX_BATCHES_PER_RUN = 10;

    @Override
    public int deleteOlderThan(LocalDateTime cutoff) {
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            int deleted = repository.deleteBatchOlderThan(cutoff, RETENTION_BATCH_SIZE);
            total += deleted;
            if (deleted < RETENTION_BATCH_SIZE) {
                break;
            }
        }
        return total;
    }

    private static int cap(int limit) {
        return Math.max(Math.min(limit, MAX_PAGE_SIZE), 1);
    }
}
