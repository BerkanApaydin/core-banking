package com.bank.app.transfer.application.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record TransferReportResponse(
    Long accountId,
    long totalTransfersCount,
    BigDecimal totalVolume,
    String currency,
    List<TransferResponse> transfers
) {
    public TransferReportResponse {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(totalVolume);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(transfers);
    }
}
