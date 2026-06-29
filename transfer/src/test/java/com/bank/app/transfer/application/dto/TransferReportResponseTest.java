package com.bank.app.transfer.application.dto;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class TransferReportResponseTest {

    @Test
    void shouldCreateWithAllFields() {
        TransferReportResponse resp = new TransferReportResponse(1L, 5L, BigDecimal.valueOf(1000), "TRY", List.of());
        assertEquals(1L, resp.accountId());
        assertEquals(5L, resp.totalTransfersCount());
        assertEquals(BigDecimal.valueOf(1000), resp.totalVolume());
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
}
