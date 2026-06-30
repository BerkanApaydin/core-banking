package com.bank.app.transfer.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferJpaRepository extends JpaRepository<TransferJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TransferJpaEntity t WHERE t.id = :id")
    Optional<TransferJpaEntity> findByIdWithLock(@Param("id") Long id);

    List<TransferJpaEntity> findBySenderAccountIdOrReceiverAccountIdOrderByCreatedAtDesc(
            Long senderId, Long receiverId, Pageable pageable);

    List<TransferJpaEntity> findBySenderAccountIdOrderByCreatedAtDesc(Long accountId, Pageable pageable);

    List<TransferJpaEntity> findByReceiverAccountIdOrderByCreatedAtDesc(Long accountId, Pageable pageable);

    List<TransferJpaEntity> findBySenderAccountIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long accountId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT t FROM TransferJpaEntity t WHERE (t.senderAccountId = :accountId OR t.receiverAccountId = :accountId) "
           + "AND t.createdAt BETWEEN :start AND :end ORDER BY t.createdAt DESC")
    List<TransferJpaEntity> findHistoryBetween(
            @Param("accountId") Long accountId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);

    long countBySenderAccountIdOrReceiverAccountId(Long senderId, Long receiverId);
}
