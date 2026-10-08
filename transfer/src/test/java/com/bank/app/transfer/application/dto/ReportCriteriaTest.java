package com.bank.app.transfer.application.dto;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class ReportCriteriaTest {

    @Test
    void shouldCreateWithAllFields() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 30, 23, 59);
        ReportCriteria rc = new ReportCriteria(1L, start, end, 0, 50);
        assertEquals(1L, rc.accountId());
        assertEquals(start, rc.startDate());
        assertEquals(end, rc.endDate());
        assertEquals(0, rc.page());
        assertEquals(50, rc.size());
    }

    @Test
    void shouldCreateWithDefaultPageAndSize() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 30, 23, 59);
        ReportCriteria rc = new ReportCriteria(1L, start, end);
        assertEquals(0, rc.page());
        assertEquals(100, rc.size());
    }

    @Test
    void shouldRejectNullAccountId() {
        assertThrows(NullPointerException.class,
                () -> new ReportCriteria(null, LocalDateTime.now(), LocalDateTime.now(), 0, 10));
    }

    @Test
    void shouldRejectNullStartDate() {
        assertThrows(NullPointerException.class,
                () -> new ReportCriteria(1L, null, LocalDateTime.now(), 0, 10));
    }

    @Test
    void shouldRejectNullEndDate() {
        assertThrows(NullPointerException.class,
                () -> new ReportCriteria(1L, LocalDateTime.now(), null, 0, 10));
    }

    @Test
    void shouldRejectNegativePage() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReportCriteria(1L, LocalDateTime.now(), LocalDateTime.now(), -1, 10));
    }

    @Test
    void shouldRejectZeroSize() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReportCriteria(1L, LocalDateTime.now(), LocalDateTime.now(), 0, 0));
    }

    @Test
    void shouldRejectSizeAboveMax() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReportCriteria(1L, LocalDateTime.now(), LocalDateTime.now(), 0, 101));
    }

    @Test
    void shouldRejectDeepOffsetWindow() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 30, 23, 59);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new ReportCriteria(1L, start, end, 200, 100));
        assertTrue(ex.getMessage().contains("keyset"));
    }

    @Test
    void shouldAcceptBoundaryOffsetWindow() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 30, 23, 59);
        ReportCriteria rc = new ReportCriteria(1L, start, end, 100, 100);
        assertEquals(100, rc.page());
    }

    @Test
    void shouldAcceptLargePageOnKeysetPath() {
        // The cursor path never pays OFFSET, so the window cap must not apply:
        // criteriaOf forces page 0 anyway, direct construction with a cursor
        // plus a large page must also survive.
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 30, 23, 59);
        LocalDateTime cursor = LocalDateTime.of(2026, 3, 1, 12, 0);
        ReportCriteria rc = new ReportCriteria(1L, start, end, 5000, 100, cursor, 42L);
        assertTrue(rc.isKeyset());
    }
}
