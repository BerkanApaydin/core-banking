package com.bank.app.transfer.application.usecase;

import com.bank.app.common.application.port.in.ReadOnlyUseCase;
import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferReportResponse;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.in.GenerateTransferReportWithTotalsQuery;
import com.bank.app.transfer.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
import com.bank.app.transfer.application.service.TransferViewEnricher;
import com.bank.app.transfer.domain.Transfer;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * API-2: items + whole-range totals in one use-case call (single
 * authorization, same read transaction). Eliminates the two-round-trip
 * inconsistency where page and totals were computed at different instants.
 */
@ReadOnlyUseCase
public class GenerateTransferReportWithTotalsQueryImpl implements GenerateTransferReportWithTotalsQuery {

    private final LoadTransferPort loadTransferPort;
    private final TransferViewEnricher viewEnricher;
    private final TransferAuthorizationService transferAuthorizationService;
    private final int maxPageSize;

    public GenerateTransferReportWithTotalsQueryImpl(LoadTransferPort loadTransferPort,
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
        int size = Math.max(Math.min(criteria.size(), maxPageSize), 1);
        AccountInfo account = transferAuthorizationService.authorizeAccountAccess(criteria.accountId(),
                "You are not authorized to generate a report for this account.");
        List<Transfer> fetched = criteria.isKeyset()
                ? loadTransferPort.findHistoryBetweenKeyset(criteria.accountId(), criteria.startDate(),
                        criteria.endDate(), criteria.cursorCreatedAt(), criteria.cursorId(), size)
                : loadTransferPort.findHistoryBetween(criteria.accountId(), criteria.startDate(),
                        criteria.endDate(), Math.max(criteria.page(), 0), size);
        boolean hasNext = fetched.size() > size;
        List<Transfer> transfers = hasNext ? fetched.subList(0, size) : fetched;
        List<TransferResponse> responseList = viewEnricher.enrich(transfers);
        BigDecimal pageVolume = transfers.stream().map(t -> t.getAmount().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LoadTransferPort.ReportTotals totals =
                loadTransferPort.summarizeRange(criteria.accountId(), criteria.startDate(), criteria.endDate());
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
        return new TransferReportResponse(criteria.accountId(), transfers.size(), pageVolume,
                account.currency(), responseList, hasNext, nextCursorCreatedAt, nextCursorId,
                totals.count(), totals.volume());
    }
}
