package com.bank.app.transfer.application.usecase;

import com.bank.app.common.application.port.in.ReadOnlyUseCase;
import com.bank.app.common.domain.AccountId;
import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferReportResponse;
import com.bank.app.transfer.application.port.in.GenerateTransferReportWithTotalsQuery;
import com.bank.app.transfer.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
import com.bank.app.transfer.application.service.TransferReportPageAssembler;
import com.bank.app.transfer.application.service.TransferViewEnricher;
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
                int size = TransferReportPageAssembler.clampSize(criteria.size(), maxPageSize);
                AccountInfo account = transferAuthorizationService.authorizeAccountAccess(
                                new AccountId(criteria.accountId()),
                                "You are not authorized to generate a report for this account.");
                TransferReportPageAssembler.ReportPage page = TransferReportPageAssembler.assemble(loadTransferPort,
                                viewEnricher, criteria, size);
                LoadTransferPort.ReportTotals totals = loadTransferPort.summarizeRange(criteria.accountId(),
                                criteria.startDate(), criteria.endDate());
                return new TransferReportResponse(criteria.accountId(), page.transfers().size(), page.pageVolume(),
                                account.currency(), page.responses(), page.hasNext(), page.nextCursorCreatedAt(),
                                page.nextCursorId(), totals.count(), totals.volume());
        }
}
