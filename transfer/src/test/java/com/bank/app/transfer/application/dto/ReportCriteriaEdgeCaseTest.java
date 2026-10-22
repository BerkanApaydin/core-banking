package com.bank.app.transfer.application.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boundary and malformed-input cases for the report window.
 *
 * <p>These are the inputs a client can craft with query parameters: reversed
 * ranges, a range one second past the 12-month cap, leap-day arithmetic,
 * far-future dates that overflow {@link LocalDateTime}, half-supplied keyset
 * cursors and an {@code int}-overflowing offset window. Each case previously
 * fell through to a broader exception (500) or could slip past the offset
 * guard.
 */
@SuppressWarnings("null")
class ReportCriteriaEdgeCaseTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Test
    void shouldRejectStartDateAfterEndDate() {
        LocalDateTime end = START.minusSeconds(1);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new ReportCriteria(ACCOUNT_ID, START, end, 0, 10));

        assertTrue(failure.getMessage().contains("Start date"));
    }

    @Test
    void shouldAcceptRangeOfExactlyTwelveMonths() {
        LocalDateTime end = START.plusMonths(12);

        ReportCriteria criteria = new ReportCriteria(ACCOUNT_ID, START, end, 0, 10);

        assertEquals(end, criteria.endDate());
    }

    @Test
    void shouldRejectRangeOneSecondOverTwelveMonths() {
        LocalDateTime end = START.plusMonths(12).plusSeconds(1);

        assertThrows(IllegalArgumentException.class,
                () -> new ReportCriteria(ACCOUNT_ID, START, end, 0, 10));
    }

    @Test
    void shouldAcceptLeapDayRangeEndingAtAdjustedTwelveMonthBoundary() {
        // 2024-02-29 + 12 months clamps to 2025-02-28T00:00 rather than
        // 2025-03-01, so the exact clamped instant is still in contract.
        LocalDateTime leapDay = LocalDateTime.of(2024, 2, 29, 0, 0);
        LocalDateTime adjustedBoundary = LocalDateTime.of(2025, 2, 28, 0, 0);

        ReportCriteria criteria = new ReportCriteria(ACCOUNT_ID, leapDay, adjustedBoundary, 0, 10);

        assertEquals(leapDay, criteria.startDate());
        assertEquals(adjustedBoundary, criteria.endDate());
    }

    @Test
    void shouldRejectLeapDayRangeOneDayPastAdjustedBoundary() {
        LocalDateTime leapDay = LocalDateTime.of(2024, 2, 29, 0, 0);
        LocalDateTime dayAfter = LocalDateTime.of(2025, 3, 1, 0, 0);

        assertThrows(IllegalArgumentException.class,
                () -> new ReportCriteria(ACCOUNT_ID, leapDay, dayAfter, 0, 10));
    }

    @Test
    void shouldRejectMaximumDateRangeWithoutOverflowingToServerError() {
        // A crafted startDate=+999999999-12-31T23:59:59 used to make
        // startDate.plusMonths(12) throw DateTimeException (HTTP 500).
        LocalDateTime max = LocalDateTime.MAX;

        assertThrows(IllegalArgumentException.class,
                () -> new ReportCriteria(ACCOUNT_ID, max, max, 0, 10));
    }

    @Test
    void shouldRejectRangeWhoseTwelveMonthProbeOverflows() {
        LocalDateTime start = LocalDateTime.MAX.minusMonths(1);

        assertThrows(IllegalArgumentException.class,
                () -> new ReportCriteria(ACCOUNT_ID, start, LocalDateTime.MAX, 0, 10));
    }

    @Test
    void shouldRejectCursorCreatedAtWithoutCursorId() {
        LocalDateTime cursor = START.plusDays(1);

        assertThrows(IllegalArgumentException.class,
                () -> new ReportCriteria(ACCOUNT_ID, START, START.plusDays(2), 0, 10, cursor, null));
    }

    @Test
    void shouldRejectCursorIdWithoutCursorCreatedAt() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReportCriteria(ACCOUNT_ID, START, START.plusDays(2), 0, 10, null, 42L));
    }

    @Test
    void shouldRejectHalfSuppliedCursorThroughConvenienceFactory() {
        assertThrows(IllegalArgumentException.class,
                () -> ReportCriteria.criteriaOf(ACCOUNT_ID, START, START.plusDays(2), 0, 10, null, 42L));
    }

    @Test
    void shouldResetPageOnKeysetPath() {
        LocalDateTime cursor = START.plusHours(5);

        ReportCriteria criteria = ReportCriteria.criteriaOf(
                ACCOUNT_ID, START, START.plusDays(2), 999, 50, cursor, 7L);

        assertTrue(criteria.isKeyset());
        assertEquals(0, criteria.page());
        assertEquals(50, criteria.size());
        assertEquals(cursor, criteria.cursorCreatedAt());
        assertEquals(7L, criteria.cursorId());
    }

    @Test
    void shouldKeepOffsetPathWhenNoCursorIsSupplied() {
        ReportCriteria criteria = ReportCriteria.criteriaOf(
                ACCOUNT_ID, START, START.plusDays(2), 3, 50, null, null);

        assertFalse(criteria.isKeyset());
        assertEquals(3, criteria.page());
    }

    @Test
    void shouldRejectOffsetWindowThatOverflowsIntMultiplication() {
        // page * size in int arithmetic overflows to a negative value and
        // would silently bypass the 10k cap; the guard must compare in long.
        int page = Integer.MAX_VALUE;

        assertThrows(IllegalArgumentException.class,
                () -> new ReportCriteria(ACCOUNT_ID, START, START.plusDays(2), page, 100));
    }

    @Test
    void shouldAcceptDeepOffsetExactlyAtBoundary() {
        ReportCriteria criteria = new ReportCriteria(
                ACCOUNT_ID, START, START.plusDays(2), 100, 100);

        assertEquals(ReportCriteria.MAX_OFFSET_WINDOW, (long) criteria.page() * criteria.size());
    }

    @Test
    void shouldRejectOffsetOneRowPastBoundary() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReportCriteria(ACCOUNT_ID, START, START.plusDays(2), 101, 100));
    }
}
