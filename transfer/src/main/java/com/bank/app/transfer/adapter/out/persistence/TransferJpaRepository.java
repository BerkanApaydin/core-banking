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
    //
    // P-1 (same pattern as summarizeRange below): UNION ALL instead of
    // {@code sender = :id OR receiver = :id} so each branch range-scans its
    // own per-side index instead of forcing a bitmap-or. Sound because
    // {@code chk_transfers_no_self_transfer} guarantees the branches are
    // disjoint (no duplicates across the union).
    @Query(value = "SELECT * FROM ("
            + "SELECT t.* FROM transfers t "
            + "WHERE t.sender_account_id = :accountId "
            + "AND t.business_created_at BETWEEN :start AND :end "
            + "UNION ALL "
            + "SELECT t.* FROM transfers t "
            + "WHERE t.receiver_account_id = :accountId "
            + "AND t.business_created_at BETWEEN :start AND :end) combined "
            + "ORDER BY combined.created_at DESC, combined.id DESC",
            nativeQuery = true)
    List<TransferJpaEntity> findHistoryBetween(
            @Param("accountId") Long accountId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);

    /**
     * Whole-range aggregates over the same business-time predicate (no entity
     * hydration, one row {@code [count, sum]}; sum is coalesced to zero when
     * empty). {@code List} (not scalar) return: same single-row-list
     * convention as the windowed history query.
     *
     * <p>P-1: UNION ALL instead of {@code sender = :id OR receiver = :id} so
     * each branch range-scans its own per-side index
     * ({@code idx_transfers_sender_business_created} /
     * {@code idx_transfers_receiver_business_created}, V28) instead of forcing
     * a bitmap-or. Sound because {@code chk_transfers_no_self_transfer}
     * guarantees the branches are disjoint (no double-count).
     */
    @Query(value = "SELECT COUNT(*), COALESCE(SUM(s.amount), 0) FROM ("
            + "SELECT t.amount FROM transfers t "
            + "WHERE t.sender_account_id = :accountId "
            + "AND t.business_created_at BETWEEN :start AND :end "
            + "UNION ALL "
            + "SELECT t.amount FROM transfers t "
            + "WHERE t.receiver_account_id = :accountId "
            + "AND t.business_created_at BETWEEN :start AND :end) s", nativeQuery = true)
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
     *
     * <p>P-1: UNION ALL per-side branches (same disjointness argument as
     * {@link #findHistoryBetween}). The {@code CAST(:cursorCreatedAt AS
     * TIMESTAMP)} guard keeps the null-cursor first page working on the
     * native path: an untyped null bind would otherwise leave the predicate
     * type unresolved. The unused {@code :cursorId} null still binds (the
     * driver infers bigint from the comparison context) but its disjunct is
     * unreachable while the cursor is null.
     */
    @Query(value = "SELECT * FROM ("
            + "SELECT t.* FROM transfers t "
            + "WHERE t.sender_account_id = :accountId "
            + "AND t.business_created_at BETWEEN :start AND :end "
            + "AND (CAST(:cursorCreatedAt AS TIMESTAMP) IS NULL OR t.created_at < :cursorCreatedAt "
            + "OR (t.created_at = :cursorCreatedAt AND t.id < :cursorId)) "
            + "UNION ALL "
            + "SELECT t.* FROM transfers t "
            + "WHERE t.receiver_account_id = :accountId "
            + "AND t.business_created_at BETWEEN :start AND :end "
            + "AND (CAST(:cursorCreatedAt AS TIMESTAMP) IS NULL OR t.created_at < :cursorCreatedAt "
            + "OR (t.created_at = :cursorCreatedAt AND t.id < :cursorId))) combined "
            + "ORDER BY combined.created_at DESC, combined.id DESC",
            nativeQuery = true)
    List<TransferJpaEntity> findHistoryBetweenKeyset(
            @Param("accountId") Long accountId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    /**
     * Crash-window scan for the pending reaper: PENDING rows whose business
     * time predates the cutoff, oldest first. The reaper locks each row
     * individually ({@code findByIdForUpdate}) and transitions it through the
     * versioned bulk UPDATE, so concurrent reaper replicas (or a racing
     * completion) surface as version conflicts, never as double marks.
     */
    @Query("SELECT t FROM TransferJpaEntity t WHERE t.status = :status "
            + "AND t.businessCreatedAt < :cutoff ORDER BY t.businessCreatedAt ASC, t.id ASC")
    List<TransferJpaEntity> findStalePending(
            @Param("status") TransferStatus status,
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable);
}
