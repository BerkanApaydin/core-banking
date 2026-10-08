package com.bank.app.transfer.application.dto;

import java.time.LocalDateTime;
import java.util.Objects;

public record ReportCriteria(
    Long accountId,
    LocalDateTime startDate,
    LocalDateTime endDate,
    int page,
    int size,
    // DB-2 keyset cursor: null = first page / legacy offset path.
    LocalDateTime cursorCreatedAt,
    Long cursorId
) {
    public ReportCriteria {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(startDate);
        Objects.requireNonNull(endDate);
        if (page < 0) throw new IllegalArgumentException("Page number must not be negative: " + page);
        if (size < 1) throw new IllegalArgumentException("Page size must be at least 1: " + size);
        if (size > 100) throw new IllegalArgumentException("Page size must be at most 100: " + size);
        if (startDate.isAfter(endDate)) throw new IllegalArgumentException("Start date must not be after end date");
        if (startDate.plusMonths(12).isBefore(endDate))
            throw new IllegalArgumentException("Report range must not exceed 12 months");
        if ((cursorCreatedAt == null) != (cursorId == null))
            throw new IllegalArgumentException("cursorCreatedAt and cursorId must both be set or both be null");
    }

    public ReportCriteria(Long accountId, LocalDateTime startDate, LocalDateTime endDate) {
        this(accountId, startDate, endDate, 0, 100, null, null);
    }

    public ReportCriteria(Long accountId, LocalDateTime startDate, LocalDateTime endDate, int page, int size) {
        this(accountId, startDate, endDate, page, size, null, null);
    }

    public boolean isKeyset() {
        return cursorCreatedAt != null && cursorId != null;
    }

    /**
     * Single construction point for the web layer: a keyset cursor (either
     * half present) selects the cursor path with page 0, otherwise the legacy
     * offset path. Keeps the three report endpoints from re-implementing the
     * same branch (DRY).
     */
    public static ReportCriteria criteriaOf(Long accountId, LocalDateTime startDate, LocalDateTime endDate,
            int page, int size, LocalDateTime cursorCreatedAt, Long cursorId) {
        if (cursorCreatedAt != null || cursorId != null) {
            return new ReportCriteria(accountId, startDate, endDate, 0, size, cursorCreatedAt, cursorId);
        }
        return new ReportCriteria(accountId, startDate, endDate, page, size);
    }
}
