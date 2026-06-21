package com.bank.app.common.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, String> {

    @Query("SELECT e.id FROM OutboxEventJpaEntity e WHERE e.processed = false AND e.deadLetter = false " +
           "AND (:partition < 0 OR e.partition = :partition) ORDER BY e.createdAt ASC")
    List<String> findUnprocessedEventIds(@Param("partition") int partition, Pageable pageable);

    @Query(value = "SELECT * FROM outbox_events WHERE id = :id AND processed = false AND dead_letter = false FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<OutboxEventJpaEntity> findByIdForUpdateSkipLocked(@Param("id") String id);

    List<OutboxEventJpaEntity> findTop10ByProcessedFalseOrderByCreatedAtAsc();
}
