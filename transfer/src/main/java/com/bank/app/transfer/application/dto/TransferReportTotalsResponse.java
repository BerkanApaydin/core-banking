package com.bank.app.transfer.application.dto;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Whole-range totals for one account and date range, aggregated in SQL.
 *
 * <p>Kept separate from {@link TransferReportResponse} on purpose: the v1
 * report contract is explicitly page-scoped, and folding range totals into it
 * would reintroduce the ambiguity v1 removed. Dashboards that need both call
 * {@code GET /report} for rows and {@code GET /report/totals} for the header.
 */
public record TransferReportTotalsResponse(
    Long accountId,
    long totalTransferCount,
    BigDecimal totalVolume,
    String currency
) {
    public TransferReportTotalsResponse {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(totalVolume);
        Objects.requireNonNull(currency);
    }
}
