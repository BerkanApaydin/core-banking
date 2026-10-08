package com.bank.app.transfer.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.bank.app.transfer.domain.TransferStatus;

public interface TransferJpaRepository extends JpaRepository<TransferJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TransferJpaEntity t WHERE t.id = :id")
    Optional<TransferJpaEntity> findByIdForUpdate(@Param("id") Long id);

    /**
     * One range scan returns the page rows plus the total match count
     * (window function, same value on every row): no second COUNT query.
     * Each element is {@code Object[]{TransferJpaEntity, Long totalCount}}.
     */
    List<Object[]> findHistoryPage(@Param("accountId") Long accountId,
                                   @Param("limit") int limit,
                                   @Param("offset") long offset);

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

    /**
     * Whole-range aggregates over the same business-time predicate (the V21
     * business-time index applies; no entity hydration, one scan).
     * Returns one row {@code [count, sum]}; sum is coalesced to zero when empty.
     * {@code List} (not scalar) return: same single-row-list convention as
     * the windowed history query.
     */
    @Query(value = "SELECT COUNT(*), COALESCE(SUM(t.amount), 0) FROM transfers t "
            + "WHERE (t.sender_account_id = :accountId OR t.receiver_account_id = :accountId) "
            + "AND t.business_created_at BETWEEN :start AND :end", nativeQuery = true)
    List<Object[]> summarizeRange(
            @Param("accountId") Long accountId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * Perf-1: single UPDATE ... WHERE id AND version for COMPLETED/CANCELLED
     * transitions — no preceding SELECT on the happy path.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TransferJpaEntity t SET t.status = :status, t.version = t.version + 1 "
            + "WHERE t.id = :id AND t.version = :version")
    int updateStatusIfVersionMatch(@Param("id") Long id,
            @Param("version") Long version,
            @Param("status") TransferStatus status);

    /**
     * DB-2/Perf-3 keyset pagination: cursor (createdAt,id) replaces OFFSET so
     * the covering index serves the range without sorting the full match set.
     * Null cursor = first page.
     */
    @Query("SELECT t FROM TransferJpaEntity t WHERE (t.senderAccountId = :accountId OR t.receiverAccountId = :accountId) "
            + "AND t.businessCreatedAt BETWEEN :start AND :end "
            + "AND (:cursorCreatedAt IS NULL OR (t.createdAt < :cursorCreatedAt OR (t.createdAt = :cursorCreatedAt AND t.id < :cursorId))) "
            + "ORDER BY t.createdAt DESC, t.id DESC")
    List<TransferJpaEntity> findHistoryBetweenKeyset(
            @Param("accountId") Long accountId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);
}
