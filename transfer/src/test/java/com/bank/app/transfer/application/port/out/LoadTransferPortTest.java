package com.bank.app.transfer.application.port.out;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

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
}
