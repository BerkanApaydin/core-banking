package com.bank.app.infrastructure.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OutboxJpaRepository extends JpaRepository<OutboxJpaEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM OutboxJpaEntity e WHERE e.processed = false AND e.deadLetter = false "
           + "AND (:partition < 0 OR e.partition = :partition) "
           + "ORDER BY e.createdAt ASC")
    List<OutboxJpaEntity> findAndLockUnprocessed(@Param("partition") int partition,
                                                  org.springframework.data.domain.Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM OutboxJpaEntity e WHERE e.id = :id")
    Optional<OutboxJpaEntity> findByIdForUpdate(@Param("id") String id);

    @Modifying
    @Query("UPDATE OutboxJpaEntity e SET e.processed = true WHERE e.id = :id")
    void markProcessed(@Param("id") String id);

    @Modifying
    @Query("UPDATE OutboxJpaEntity e SET e.processed = false, e.retryCount = :retryCount, "
           + "e.lastError = :error WHERE e.id = :id")
    void markFailed(@Param("id") String id, @Param("error") String error,
                    @Param("retryCount") int retryCount);

    @Modifying
    @Query("UPDATE OutboxJpaEntity e SET e.deadLetter = true, e.retryCount = :retryCount, "
           + "e.lastError = :error WHERE e.id = :id")
    void markDeadLetter(@Param("id") String id, @Param("error") String error,
                        @Param("retryCount") int retryCount);
}
