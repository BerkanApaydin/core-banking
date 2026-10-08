package com.bank.app.transfer.application.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Null contract for whole-range totals: a missing volume or currency must fail
 * fast with 400 (record validation) instead of leaking a null into JSON.
 */
@DisplayName("TransferReportTotalsResponse")
class TransferReportTotalsResponseTest {

    @Test
    @DisplayName("should carry whole-range totals")
    void shouldCarryTotals() {
        // Arrange + Act
        var response = new TransferReportTotalsResponse(
                1L, 5L, new BigDecimal("500.00"), "TRY");

        // Assert
        assertThat(response.accountId()).isEqualTo(1L);
        assertThat(response.totalTransferCount()).isEqualTo(5L);
        assertThat(response.totalVolume()).isEqualByComparingTo("500.00");
        assertThat(response.currency()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("should accept an empty range as zero totals")
    void shouldAcceptEmptyRange() {
        // Arrange + Act
        var response = new TransferReportTotalsResponse(
                1L, 0L, BigDecimal.ZERO, "TRY");

        // Assert
        assertThat(response.totalTransferCount()).isZero();
        assertThat(response.totalVolume()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("should reject null account id, volume or currency")
    void shouldRejectNulls() {
        assertThatThrownBy(() -> new TransferReportTotalsResponse(
                null, 5L, BigDecimal.ZERO, "TRY"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransferReportTotalsResponse(
                1L, 5L, null, "TRY"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransferReportTotalsResponse(
                1L, 5L, BigDecimal.ZERO, null))
                .isInstanceOf(NullPointerException.class);
    }
}
