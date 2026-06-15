package com.bank.app.transfer.application.dto;

import java.time.LocalDateTime;

public record ReportCriteria(
    Long accountId,
    LocalDateTime startDate,
    LocalDateTime endDate
) {}
