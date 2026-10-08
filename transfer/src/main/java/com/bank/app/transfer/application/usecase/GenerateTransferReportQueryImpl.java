package com.bank.app.transfer.application.usecase;

import com.bank.app.common.application.port.in.ReadOnlyUseCase;
import com.bank.app.common.domain.AccountId;
import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferReportResponse;
import com.bank.app.transfer.application.port.in.GenerateTransferReportQuery;
import com.bank.app.transfer.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
import com.bank.app.transfer.application.service.TransferReportPageAssembler;
import com.bank.app.transfer.application.service.TransferViewEnricher;
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
        int size = TransferReportPageAssembler.clampSize(criteria.size(), maxPageSize);

        AccountInfo account = transferAuthorizationService.authorizeAccountAccess(
                new AccountId(criteria.accountId()),
                "You are not authorized to generate a report for this account.");

        // DB-2/Perf-3: page assembly (keyset or offset, single range scan with
        // +1 over-fetch) is shared with the with-totals variant.
        TransferReportPageAssembler.ReportPage page = TransferReportPageAssembler.assemble(loadTransferPort,
                viewEnricher, criteria, size);

        return new TransferReportResponse(
                criteria.accountId(),
                page.transfers().size(),
                page.pageVolume(),
                account.currency(),
                page.responses(),
                page.hasNext(),
                page.nextCursorCreatedAt(),
                page.nextCursorId(),
                null,
                null);
    }
}
