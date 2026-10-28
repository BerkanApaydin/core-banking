package com.bank.app.transfer.application.port.out;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoadTransferPortTest {

    @Test
    void shouldRejectNegativeReportTotalsCount() {
        assertThatThrownBy(() -> new LoadTransferPort.ReportTotals(-1, BigDecimal.ZERO))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void shouldNormalizeNullReportTotalsVolumeToZero() {
        LoadTransferPort.ReportTotals totals = new LoadTransferPort.ReportTotals(3, null);

        assertThat(totals.count()).isEqualTo(3);
        assertThat(totals.volume()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldDefensivelyCopyHistoryPageItems() {
        var source = new ArrayList<com.bank.app.transfer.domain.Transfer>();
        var page = new LoadTransferPort.HistoryPage(source, 0);

        source.add(null);

        assertThat(page.items()).isEmpty();
        assertThatThrownBy(() -> page.items().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldNormalizeNullHistoryPageItemsToEmpty() {
        var page = new LoadTransferPort.HistoryPage(null, 0);

        assertThat(page.items()).isEmpty();
    }
}
