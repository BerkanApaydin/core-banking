package com.bank.app.transfer.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record TransferReportResponse(
    Long accountId,
    long totalTransfersCount,
    BigDecimal totalVolume,
    String currency,
    List<TransferResponse> transfers
) {}
