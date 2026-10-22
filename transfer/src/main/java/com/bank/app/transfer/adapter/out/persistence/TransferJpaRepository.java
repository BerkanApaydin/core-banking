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
     * Rows-only history page.
     *
     * <p>The former windowed variant ({@code COUNT(*) OVER()} over the whole
     * sender/receiver union) was removed: PostgreSQL can only evaluate a
     * window function after materialising and sorting the entire union, so a
     * 20-row page cost O(n) over the account's whole transfer history on the
     * hottest read path. The exact total comes from {@link #countHistory}
     * (an index-only count, no sort) — the same shape every page uses.
     */
    @Query(value = "SELECT u.* FROM ("
            + "SELECT t.* FROM transfers t WHERE t.sender_account_id = :accountId "
            + "UNION ALL "
            + "SELECT t.* FROM transfers t WHERE t.receiver_account_id = :accountId) u "
            + "ORDER BY u.created_at DESC, u.id DESC LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<TransferJpaEntity> findHistoryRows(@Param("accountId") Long accountId,
                                            @Param("limit") int limit,
                                            @Param("offset") long offset);

    /**
     * P-1: standalone history total (covering-index count, no row sort).
     */
    @Query(value = "SELECT COUNT(*) FROM ("
            + "SELECT t.id FROM transfers t WHERE t.sender_account_id = :accountId "
            + "UNION ALL "
            + "SELECT t.id FROM transfers t WHERE t.receiver_account_id = :accountId) u",
            nativeQuery = true)
    long countHistory(@Param("accountId") Long accountId);

    // Business-time filter on business_created_at (V20, NOT NULL since V28).
    // The previous COALESCE(business_created_at, created_at) fallback defeated
    // the V21 business-time index (a function over the column cannot use a plain
    // btree range scan). Legacy rows were backfilled in V20/V28, so the direct
    // column predicate keeps the index usable. Ordering uses the same
    // business_created_at key as the report cursor: nextCursorCreatedAt is
    // Transfer.createdAt (the domain/business instant, mapped to
    // business_created_at), so ordering by the auditing created_at would skip
    // or duplicate rows whenever the two timestamps differ (V61 indexes).
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
            + "ORDER BY combined.business_created_at DESC, combined.id DESC",
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
     * ({@code idx_transfers_sender_business_created_id} /
     * {@code idx_transfers_receiver_business_created_id}, V61) instead of forcing
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
     *
     * <p>{@code updatedAt} is refreshed here for the same reason the
     * account twin refreshes it: the bulk path bypasses
     * {@code AuditingEntityListener}, so without the explicit write the
     * column would stay NULL and the transfer audit trail would lose its
     * "last changed" information.
     *
     * <p>No {@code clearAutomatically} (R-A03): clearing detaches every
     * managed entity in the persistence context, while this adapter returns a
     * freshly reconstructed aggregate and never re-reads the managed row after
     * the bulk write. The 0-row path uses {@link #countById(Long)}, a count
     * query that bypasses the persistence context.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE TransferJpaEntity t SET t.status = :status, t.version = t.version + 1, "
            + "t.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE t.id = :id AND t.version = :version")
    int updateStatusIfVersionMatch(@Param("id") Long id,
            @Param("version") Long version,
            @Param("status") TransferStatus status);

    /**
     * Existence check that bypasses the persistence context. Used only on the
     * 0-row bulk-update path so a stale managed copy cannot turn a concurrent
     * delete into an optimistic-lock conflict.
     */
    @Query("SELECT COUNT(t) FROM TransferJpaEntity t WHERE t.id = :id")
    long countById(@Param("id") Long id);

    /**
     * DB-2/Perf-3 keyset pagination: cursor (businessCreatedAt,id) replaces
     * OFFSET so the covering index serves the range without sorting the full
     * match set. Null cursor = first page. The cursor key MUST be the same
     * business timestamp the report filters and orders by: it comes from
     * Transfer.createdAt (mapped to business_created_at), not from the
     * auditing created_at.
     *
     * <p>P-1: UNION ALL per-side branches (same disjointness argument as
     * {@link #findHistoryBetween}). The {@code CAST(:cursorCreatedAt AS
     * TIMESTAMP)} guard keeps the null-cursor first page working on the
     * native path: an untyped null bind would otherwise leave the predicate
     * type unresolved. The unused {@code :cursorId} null still binds (the
     * driver infers bigint from the comparison context) but its disjunct is
     * unreachable while the cursor is null.
     *
     * <p>PERF-4: the cursor predicate uses ANSI row comparison
     * {@code (business_created_at, id) < (cursor)} — only the ROW() syntax
     * lets PostgreSQL apply its row-comparison index optimization against the
     * V61 covering indexes; the {@code OR} disjunction falls back to BitmapOr.
     */
    @Query(value = "SELECT * FROM ("
            + "SELECT t.* FROM transfers t "
            + "WHERE t.sender_account_id = :accountId "
            + "AND t.business_created_at BETWEEN :start AND :end "
            + "AND (CAST(:cursorCreatedAt AS TIMESTAMP) IS NULL OR (t.business_created_at, t.id) < (:cursorCreatedAt, :cursorId)) "
            + "UNION ALL "
            + "SELECT t.* FROM transfers t "
            + "WHERE t.receiver_account_id = :accountId "
            + "AND t.business_created_at BETWEEN :start AND :end "
            + "AND (CAST(:cursorCreatedAt AS TIMESTAMP) IS NULL OR (t.business_created_at, t.id) < (:cursorCreatedAt, :cursorId))) combined "
            + "ORDER BY combined.business_created_at DESC, combined.id DESC",
            nativeQuery = true)
    List<TransferJpaEntity> findHistoryBetweenKeyset(
            @Param("accountId") Long accountId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);
}
