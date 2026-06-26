package com.bank.app.transfer.application.dto;

import java.time.LocalDateTime;
import java.util.Objects;

public record ReportCriteria(
    Long accountId,
    LocalDateTime startDate,
    LocalDateTime endDate,
    int page,
    int size
) {
    public ReportCriteria {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(startDate);
        Objects.requireNonNull(endDate);
        if (page < 0) throw new IllegalArgumentException("Sayfa numarası negatif olamaz: " + page);
        if (size < 1) throw new IllegalArgumentException("Sayfa boyutu en az 1 olmalıdır: " + size);
        if (size > 100) throw new IllegalArgumentException("Sayfa boyutu en fazla 100 olabilir: " + size);
    }

    public ReportCriteria(Long accountId, LocalDateTime startDate, LocalDateTime endDate) {
        this(accountId, startDate, endDate, 0, 100);
    }
}
