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
 * {@code hasNext} tells clients whether another page exists for the same
 * criteria at query time; separate page requests are not a snapshot.
 */
public record TransferReportResponse(
    Long accountId,
    long pageTransferCount,
    BigDecimal pageVolume,
    String currency,
    List<TransferResponse> transfers,
    boolean hasNext
) {
    public TransferReportResponse(Long accountId, long pageTransferCount, BigDecimal pageVolume,
                                  String currency, List<TransferResponse> transfers) {
        this(accountId, pageTransferCount, pageVolume, currency, transfers, false);
    }

    public TransferReportResponse {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(pageVolume);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(transfers);
    }
}
