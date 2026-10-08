package com.bank.app.transfer.application.service;

import com.bank.app.transfer.application.dto.TransferReportResponse;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.domain.TransferStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TransferReportEtagService")
class TransferReportEtagServiceTest {

    private static TransferResponse item(long id, String amount) {
        return new TransferResponse(id, TransferStatus.COMPLETED, new BigDecimal(amount), "TRY",
                LocalDateTime.of(2026, 6, 1, 12, 0),
                "TR770006200000000000000111", "TR870006200000000000000222", 1L, 2L);
    }

    private static TransferReportResponse report(String pageVolume, List<TransferResponse> items,
            Long totalCount, String totalVolume) {
        return new TransferReportResponse(1L, items.size(), new BigDecimal(pageVolume), "TRY",
                items, false, null, null, totalCount,
                totalVolume == null ? null : new BigDecimal(totalVolume));
    }

    @Test
    @DisplayName("normalizes equivalent volumes to the same tag (10.0 vs 10.00)")
    void normalizesEquivalentVolumes() {
        String compact = TransferReportEtagService.etagFor(report("10.0", List.of(item(1, "10.0")), 1L, "10.0"));
        String padded = TransferReportEtagService.etagFor(report("10.00", List.of(item(1, "10.00")), 1L, "10.00"));

        assertThat(padded).isEqualTo(compact);
    }

    @Test
    @DisplayName("changes the tag when page items differ despite equal count and volume")
    void distinguishesDifferentItems() {
        String first = TransferReportEtagService.etagFor(report("20.00", List.of(item(1, "20.00")), 1L, "20.00"));
        String second = TransferReportEtagService.etagFor(report("20.00", List.of(item(2, "20.00")), 1L, "20.00"));

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    @DisplayName("changes the tag when whole-range totals move on an identical page")
    void distinguishesMovedTotals() {
        String before = TransferReportEtagService.etagFor(report("20.00", List.of(item(1, "20.00")), 1L, "20.00"));
        String after = TransferReportEtagService.etagFor(report("20.00", List.of(item(1, "20.00")), 2L, "40.00"));

        assertThat(after).isNotEqualTo(before);
    }

    @Test
    @DisplayName("emits a short weak validator shape")
    void emitsWeakValidatorShape() {
        String etag = TransferReportEtagService.etagFor(report("0.00", List.of(), 0L, "0.00"));

        assertThat(etag).startsWith("W/\"report-").endsWith("\"");
    }
}
