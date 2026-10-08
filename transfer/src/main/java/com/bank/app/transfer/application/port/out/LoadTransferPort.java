package com.bank.app.transfer.application.port.out;

import com.bank.app.transfer.domain.Transfer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public interface LoadTransferPort {
    Optional<Transfer> findById(Long id);
    Optional<Transfer> findByIdForUpdate(Long id);

    /** One page plus its exact total, served by a single range scan. */
    record HistoryPage(List<Transfer> items, long totalElements) {
    }

    HistoryPage findHistoryPage(Long accountId, int page, int size);

    /**
     * Returns the logical page plus one over-fetch row (up to {@code size + 1}
     * rows): callers trim to {@code size} and derive {@code hasNext} without
     * a second query.
     */
    List<Transfer> findHistoryBetween(Long accountId, LocalDateTime start, LocalDateTime end, int page, int size);

    /**
     * DB-2/Perf-3 keyset pagination: cursor (createdAt,id) replaces OFFSET.
     * Null cursor = first page. Limit is the logical page size; the adapter
     * over-fetches one row to derive hasNext without a second query.
     */
    List<Transfer> findHistoryBetweenKeyset(Long accountId, LocalDateTime start, LocalDateTime end,
            LocalDateTime cursorCreatedAt, Long cursorId, int size);

    /**
     * Whole-range aggregates in one indexed scan (COUNT + SUM over the same
     * business-time predicate the paged queries use). Backs the report-totals
     * endpoint so dashboards never page through history to sum it.
     */
    record ReportTotals(long count, BigDecimal volume) {
        public ReportTotals {
            if (count < 0) throw new IllegalArgumentException("Count must not be negative: " + count);
            volume = Objects.requireNonNullElse(volume, BigDecimal.ZERO);
        }
    }

    ReportTotals summarizeRange(Long accountId, LocalDateTime start, LocalDateTime end);
}
