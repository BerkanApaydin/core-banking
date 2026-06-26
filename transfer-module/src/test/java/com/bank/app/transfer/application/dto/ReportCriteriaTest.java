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
}
