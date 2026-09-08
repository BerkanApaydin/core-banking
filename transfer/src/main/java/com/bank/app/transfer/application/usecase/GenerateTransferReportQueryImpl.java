package com.bank.app.transfer.application.usecase;

import com.bank.app.common.application.port.in.ReadOnlyUseCase;
import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferReportResponse;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.in.GenerateTransferReportQuery;
import com.bank.app.transfer.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
import com.bank.app.transfer.application.service.TransferViewEnricher;
import com.bank.app.transfer.domain.Transfer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@ReadOnlyUseCase
public class GenerateTransferReportQueryImpl implements GenerateTransferReportQuery {

    private final LoadTransferPort loadTransferPort;
    private final TransferViewEnricher viewEnricher;
    private final TransferAuthorizationService transferAuthorizationService;
    private final int maxPageSize;

    public GenerateTransferReportQueryImpl(LoadTransferPort loadTransferPort,
                                         TransferViewEnricher viewEnricher,
                                         TransferAuthorizationService transferAuthorizationService,
                                         int maxPageSize) {
        this.loadTransferPort = loadTransferPort;
        this.viewEnricher = viewEnricher;
        this.transferAuthorizationService = transferAuthorizationService;
        this.maxPageSize = maxPageSize > 0 ? maxPageSize : 100;
    }

    @Override
    public TransferReportResponse execute(ReportCriteria criteria) {
        Objects.requireNonNull(criteria, "Criteria must not be null");
        Long accountId = Objects.requireNonNull(criteria.accountId(), "Account ID must not be null");
        LocalDateTime startDate = Objects.requireNonNull(criteria.startDate(), "Start date must not be null");
        LocalDateTime endDate = Objects.requireNonNull(criteria.endDate(), "End date must not be null");

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must not be after end date.");
        }
        if (startDate.plusMonths(12).isBefore(endDate)) {
            throw new IllegalArgumentException("Report range must be at most 12 months.");
        }

        int page = Math.max(criteria.page(), 0);
        int size = Math.max(Math.min(criteria.size(), maxPageSize), 1);

        AccountInfo account = transferAuthorizationService.authorizeAccountAccess(accountId, "You are not authorized to generate a report for this account.");

        List<Transfer> transfers = loadTransferPort.findHistoryBetween(
            accountId,
            startDate,
            endDate,
            page,
            size
        );

        // Batch load account IBANs to avoid N+1 query problem (see TransferViewEnricher)
        List<TransferResponse> responseList = viewEnricher.enrich(transfers);

        // Page-scoped aggregates (see TransferReportResponse): totals across the
        // whole date range would need separate aggregate queries.
        BigDecimal pageVolume = transfers.stream()
            .map(t -> t.getAmount().amount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new TransferReportResponse(
            criteria.accountId(),
            transfers.size(),
            pageVolume,
            account.currency(),
            responseList
        );
    }
}
