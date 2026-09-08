package com.bank.app.transfer.application.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Date-range transfer report for one account.
 *
 * <p>BREAKING (v1): {@code pageTransferCount}/{@code pageVolume} replace the
 * former {@code totalTransfersCount}/{@code totalVolume}, which misleadingly
 * implied report-wide totals while only aggregating the returned page.
 */
public record TransferReportResponse(
    Long accountId,
    long pageTransferCount,
    BigDecimal pageVolume,
    String currency,
    List<TransferResponse> transfers
) {
    public TransferReportResponse {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(pageVolume);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(transfers);
    }
}
