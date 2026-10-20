package com.bank.app.transfer.application.service;

import com.bank.app.common.domain.AccountId;
import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferReportResponse;
import com.bank.app.transfer.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import java.util.Objects;

/**
 * Shared report flow: authorize once, assemble one page, optionally add
 * whole-range totals from the same read transaction.
 *
 * <p>
 * The paged-only and paged-with-totals variants differ only in whether
 * {@code summarizeRange} runs; keeping that switch here prevents the
 * authorization, sizing, cursor, and response-shape rules from drifting.
 */
public abstract class AbstractTransferReportQuery {

    private final LoadTransferPort loadTransferPort;
    private final TransferViewEnricher viewEnricher;
    private final TransferAuthorizationService transferAuthorizationService;
    private final int maxPageSize;

    protected AbstractTransferReportQuery(LoadTransferPort loadTransferPort,
            TransferViewEnricher viewEnricher,
            TransferAuthorizationService transferAuthorizationService,
            int maxPageSize) {
        this.loadTransferPort = loadTransferPort;
        this.viewEnricher = viewEnricher;
        this.transferAuthorizationService = transferAuthorizationService;
        this.maxPageSize = maxPageSize > 0 ? maxPageSize : 100;
    }

    protected final TransferReportResponse execute(ReportCriteria criteria, boolean includeTotals) {
        Objects.requireNonNull(criteria, "Criteria must not be null");
        // Date-range invariants live in ReportCriteria's compact constructor
        // (single source); no duplicate validation here.
        int size = TransferReportPageAssembler.clampSize(criteria.size(), maxPageSize);

        AccountInfo account = transferAuthorizationService.authorizeAccountAccess(
                new AccountId(criteria.accountId()),
                "You are not authorized to generate a report for this account.");

        TransferReportPageAssembler.ReportPage page = TransferReportPageAssembler.assemble(loadTransferPort,
                viewEnricher, criteria, size);

        LoadTransferPort.ReportTotals totals = includeTotals
                ? loadTransferPort.summarizeRange(criteria.accountId(),
                        criteria.startDate(), criteria.endDate())
                : null;

        return new TransferReportResponse(
                criteria.accountId(),
                page.transfers().size(),
                page.pageVolume(),
                account.currency(),
                page.responses(),
                page.hasNext(),
                page.nextCursorCreatedAt(),
                page.nextCursorId(),
                totals == null ? null : totals.count(),
                totals == null ? null : totals.volume());
    }
}
