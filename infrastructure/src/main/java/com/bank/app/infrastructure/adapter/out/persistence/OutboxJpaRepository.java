package com.bank.app.infrastructure.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OutboxJpaRepository extends JpaRepository<OutboxJpaEntity, String> {

    @Query("SELECT COUNT(e) FROM OutboxJpaEntity e WHERE e.processed = false AND e.deadLetter = false "
           + "AND (e.partition < 0 OR e.partition >= :partitionCount)")
    long countPendingOutsidePartitionRange(@Param("partitionCount") int partitionCount);

    // JPA/Hibernate lock timeout values: 0 = NOWAIT, -2 = SKIP LOCKED,
    // -1 = wait forever. Multi-pod pollers must SKIP LOCKED so replicas
    // never block on each other's rows (K4/D6).
    //
    // V57: next_attempt_at gates the retry budget. A failed event is not
    // selected again until its exponential backoff has elapsed, so a poison
    // event can no longer burn every retry within a couple of poll cycles.
    // The predicate is NULL-friendly: an event that never failed is always
    // eligible.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT e FROM OutboxJpaEntity e WHERE e.processed = false AND e.deadLetter = false "
           + "AND (:partition < 0 OR e.partition = :partition) "
           + "AND (e.nextAttemptAt IS NULL OR e.nextAttemptAt <= :now) "
           + "ORDER BY e.createdAt ASC")
    List<OutboxJpaEntity> findAndLockUnprocessed(@Param("partition") int partition,
                                                  @Param("now") LocalDateTime now,
                                                  Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT e FROM OutboxJpaEntity e WHERE e.id = :id AND e.processed = false AND e.deadLetter = false")
    Optional<OutboxJpaEntity> findByIdForUpdateSkipLocked(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OutboxJpaEntity e SET e.processed = true, e.processedAt = CURRENT_TIMESTAMP, "
            + "e.nextAttemptAt = NULL "
            + "WHERE e.id = :id AND e.processed = false AND e.deadLetter = false")
    void markProcessed(@Param("id") String id);

    /**
     * Records a failed attempt and arms the backoff: the event is not
     * selected again until {@code CURRENT_TIMESTAMP + backoffSeconds}.
     * The delay is computed by the adapter (exponential, capped) and applied
     * with the DATABASE clock so the row and the poll schedule agree.
     *
     * <p>Native SQL: the backoff needs PostgreSQL's
     * {@code make_interval(secs => ...)}, whose named-argument syntax HQL
     * cannot parse.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE outbox_events SET processed = false, retry_count = :retryCount, "
            + "last_error = :error, "
            + "next_attempt_at = CURRENT_TIMESTAMP + make_interval(secs => :backoffSeconds) "
            + "WHERE id = :id AND processed = false AND dead_letter = false",
            nativeQuery = true)
    void markFailed(@Param("id") String id, @Param("error") String error,
                    @Param("retryCount") int retryCount,
                    @Param("backoffSeconds") int backoffSeconds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OutboxJpaEntity e SET e.deadLetter = true, e.retryCount = :retryCount, "
            + "e.lastError = :error, e.nextAttemptAt = NULL "
            + "WHERE e.id = :id AND e.processed = false AND e.deadLetter = false")
    void markDeadLetter(@Param("id") String id, @Param("error") String error,
                        @Param("retryCount") int retryCount);

    /**
     * Retention: only acknowledged, non-dead rows. Dead letters stay for
     * investigation; unprocessed rows are the redelivery source.
     * {@code COALESCE} covers rows processed before processed_at was stamped.
     *
     * <p>Bounded batch (same pattern as audit retention): the IN-subquery
     * scopes the DELETE through the retention range so one schedule never
     * holds the whole tail in a single transaction.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM outbox_events WHERE id IN "
            + "(SELECT id FROM outbox_events WHERE processed = true AND dead_letter = false "
            + "AND COALESCE(processed_at, created_at) < :cutoff "
            + "ORDER BY COALESCE(processed_at, created_at), id LIMIT :batch)",
            nativeQuery = true)
    int deleteProcessedBatchBefore(@Param("cutoff") LocalDateTime cutoff, @Param("batch") int batch);
}
