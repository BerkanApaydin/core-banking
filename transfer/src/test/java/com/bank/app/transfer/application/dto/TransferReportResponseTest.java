package com.bank.app.transfer.application.dto;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class TransferReportResponseTest {

    @Test
    void shouldCreateWithAllFields() {
        TransferReportResponse resp = new TransferReportResponse(1L, 5L, BigDecimal.valueOf(1000), "TRY", List.of());
        assertEquals(1L, resp.accountId());
        assertEquals(5L, resp.pageTransferCount());
        assertEquals(BigDecimal.valueOf(1000), resp.pageVolume());
        assertEquals("TRY", resp.currency());
        assertTrue(resp.transfers().isEmpty());
    }

    @Test
    void shouldRejectNullAccountId() {
        assertThrows(NullPointerException.class,
                () -> new TransferReportResponse(null, 0L, BigDecimal.ZERO, "TRY", List.of()));
    }

    @Test
    void shouldRejectNullTotalVolume() {
        assertThrows(NullPointerException.class,
                () -> new TransferReportResponse(1L, 0L, null, "TRY", List.of()));
    }

    @Test
    void shouldRejectNullCurrency() {
        assertThrows(NullPointerException.class,
                () -> new TransferReportResponse(1L, 0L, BigDecimal.ZERO, null, List.of()));
    }

    @Test
    void shouldRejectNullTransfersList() {
        assertThrows(NullPointerException.class,
                () -> new TransferReportResponse(1L, 0L, BigDecimal.ZERO, "TRY", null));
    }

    @Test
    void shouldDefaultCursorAndTotalsWhenHasNextIsProvided() {
        TransferReportResponse resp = new TransferReportResponse(
                1L, 2L, BigDecimal.valueOf(200), "TRY", List.of(), true);

        assertTrue(resp.hasNext());
        assertNull(resp.nextCursorCreatedAt());
        assertNull(resp.nextCursorId());
        assertNull(resp.totalCount());
        assertNull(resp.totalVolume());
    }

    @Test
    void shouldNotExposeTheCallerOwnedTransfersList() {
        var source = new ArrayList<TransferResponse>();
        TransferReportResponse resp = new TransferReportResponse(
                1L, 0L, BigDecimal.ZERO, "TRY", source);

        source.add(null);

        assertTrue(resp.transfers().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> resp.transfers().add(null));
    }

    @Test
    void shouldExposeHasNextFalseWithoutCursor() {
        TransferReportResponse resp = new TransferReportResponse(
                1L, 2L, BigDecimal.valueOf(200), "TRY", List.of(), false);

        assertFalse(resp.hasNext());
        assertNull(resp.nextCursorCreatedAt());
        assertNull(resp.nextCursorId());
    }
}
