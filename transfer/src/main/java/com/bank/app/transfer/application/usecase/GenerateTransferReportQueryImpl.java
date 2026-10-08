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
        // Date-range invariants live in ReportCriteria's compact constructor
        // (single source); no duplicate validation here.
        Long accountId = criteria.accountId();
        LocalDateTime startDate = criteria.startDate();
        LocalDateTime endDate = criteria.endDate();

        int page = Math.max(criteria.page(), 0);
        int size = Math.max(Math.min(criteria.size(), maxPageSize), 1);

        AccountInfo account = transferAuthorizationService.authorizeAccountAccess(accountId, "You are not authorized to generate a report for this account.");

        // DB-2/Perf-3: keyset cursor when supplied (no OFFSET/sort of the full
        // match set); legacy offset path otherwise. Both over-fetch one row so
        // content and hasNext come from a single range scan.
        List<Transfer> fetched = criteria.isKeyset()
            ? loadTransferPort.findHistoryBetweenKeyset(
                accountId, startDate, endDate,
                criteria.cursorCreatedAt(), criteria.cursorId(), size)
            : loadTransferPort.findHistoryBetween(
                accountId,
                startDate,
                endDate,
                page,
                size
            );
        boolean hasNext = fetched.size() > size;
        List<Transfer> transfers = hasNext ? fetched.subList(0, size) : fetched;

        // Batch load account IBANs to avoid N+1 query problem (see TransferViewEnricher)
        List<TransferResponse> responseList = viewEnricher.enrich(transfers);

        // Page-scoped aggregates (see TransferReportResponse): totals across the
        // whole date range would need separate aggregate queries.
        BigDecimal pageVolume = transfers.stream()
            .map(t -> t.getAmount().amount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Keyset cursor for the next page: last row's (createdAt,id).
        String nextCursorCreatedAt = null;
        Long nextCursorId = null;
        // hasNext implies a non-empty page (size >= 1, so the trimmed subList
        // cannot be empty): no defensive isEmpty check — dead conditions hide
        // real branches from mutation testing.
        if (hasNext) {
            Transfer last = transfers.get(transfers.size() - 1);
            nextCursorCreatedAt = last.getCreatedAt().toString();
            nextCursorId = last.getId();
        }

        return new TransferReportResponse(
            criteria.accountId(),
            transfers.size(),
            pageVolume,
            account.currency(),
            responseList,
            hasNext,
            nextCursorCreatedAt,
            nextCursorId,
            null,
            null
        );
    }
}
