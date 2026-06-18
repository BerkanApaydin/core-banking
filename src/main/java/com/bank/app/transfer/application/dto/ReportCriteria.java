package com.bank.app.transfer.application.dto;

import java.time.LocalDateTime;

public record ReportCriteria(
    Long accountId,
    LocalDateTime startDate,
    LocalDateTime endDate,
    int page,
    int size
) {
    public ReportCriteria(Long accountId, LocalDateTime startDate, LocalDateTime endDate) {
        this(accountId, startDate, endDate, 0, 100);
    }
}
