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
    Optional<TransferJpaEntity> findByIdForUpdate(@Param("id") Long id);

    List<TransferJpaEntity> findBySenderAccountIdOrReceiverAccountIdOrderByCreatedAtDescIdDesc(
            Long senderId, Long receiverId, Pageable pageable);

    // Business-time filter on business_created_at (V20, NOT NULL since V28).
    // The previous COALESCE(business_created_at, created_at) fallback defeated
    // the V21 business-time index (a function over the column cannot use a plain
    // btree range scan). Legacy rows were backfilled in V20/V28, so the direct
    // column predicate keeps the index usable. Ordering stays on the auditing
    // created_at (insert order) by design.
    @Query("SELECT t FROM TransferJpaEntity t WHERE (t.senderAccountId = :accountId OR t.receiverAccountId = :accountId) "
           + "AND t.businessCreatedAt BETWEEN :start AND :end "
           + "ORDER BY t.createdAt DESC, t.id DESC")
    List<TransferJpaEntity> findHistoryBetween(
            @Param("accountId") Long accountId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);

    long countBySenderAccountIdOrReceiverAccountId(Long senderId, Long receiverId);
}
