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
    boolean hasNext,
    // DB-2 keyset cursor for the next page (null when hasNext=false).
    String nextCursorCreatedAt,
    Long nextCursorId,
    // API-2: whole-range aggregates in the same response so items+totals are
    // computed from one criteria without a second round trip. Null when the
    // caller used the paged-only query.
    Long totalCount,
    BigDecimal totalVolume
) {
    public TransferReportResponse(Long accountId, long pageTransferCount, BigDecimal pageVolume,
                                  String currency, List<TransferResponse> transfers) {
        this(accountId, pageTransferCount, pageVolume, currency, transfers, false, null, null, null, null);
    }

    public TransferReportResponse(Long accountId, long pageTransferCount, BigDecimal pageVolume,
                                  String currency, List<TransferResponse> transfers, boolean hasNext) {
        this(accountId, pageTransferCount, pageVolume, currency, transfers, hasNext, null, null, null, null);
    }

    public TransferReportResponse {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(pageVolume);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(transfers);
    }
}
