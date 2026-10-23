package com.bank.app.audit.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogJpaEntity, Long> {

    List<AuditLogJpaEntity> findAllByOrderByTimestampDescIdDesc(Pageable pageable);

    List<AuditLogJpaEntity> findByActorUserIdOrderByTimestampDescIdDesc(Long actorUserId, Pageable pageable);

    List<AuditLogJpaEntity> findByTimestampBetweenOrderByTimestampDescIdDesc(
            LocalDateTime from, LocalDateTime to, Pageable pageable);

    /**
     * Bounded retention batch. V62 installs an append-only trigger on
     * audit_logs; the {@code delete_audit_logs_before} function is the only
     * path allowed to mutate rows, and it scopes the batch through the
     * retention index ({@code idx_audit_logs_timestamp_id}, V40). The
     * transaction boundary is per batch, so the transaction-local maintenance
     * flag cannot leak on a pooled connection.
     */
    @Transactional
    @Query(value = "SELECT delete_audit_logs_before(:cutoff, :batch)",
            nativeQuery = true)
    int deleteBatchOlderThan(@Param("cutoff") LocalDateTime cutoff, @Param("batch") int batch);
}
