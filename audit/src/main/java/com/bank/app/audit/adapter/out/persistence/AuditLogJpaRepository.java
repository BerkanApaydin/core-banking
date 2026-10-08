package com.bank.app.audit.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogJpaEntity, Long> {

    List<AuditLogJpaEntity> findAllByOrderByTimestampDescIdDesc(Pageable pageable);

    List<AuditLogJpaEntity> findByActorUserIdOrderByTimestampDescIdDesc(Long actorUserId, Pageable pageable);

    List<AuditLogJpaEntity> findByTimestampBetweenOrderByTimestampDescIdDesc(
            LocalDateTime from, LocalDateTime to, Pageable pageable);

    /**
     * Bounded retention batch: Postgres forbids LIMIT directly in DELETE, so
     * the batch scopes through an IN-subquery over the retention index
     * ({@code idx_audit_logs_timestamp_id}, V40). One unbounded DELETE would
     * hold row locks for the whole 365-day tail in a single transaction; the
     * adapter loops these batches with a per-run cap instead.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM audit_logs WHERE id IN "
            + "(SELECT id FROM audit_logs WHERE timestamp < :cutoff "
            + "ORDER BY timestamp, id LIMIT :batch)",
            nativeQuery = true)
    int deleteBatchOlderThan(@Param("cutoff") LocalDateTime cutoff, @Param("batch") int batch);
}
