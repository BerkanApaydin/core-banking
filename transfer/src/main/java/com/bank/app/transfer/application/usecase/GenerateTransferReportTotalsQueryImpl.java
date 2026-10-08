package com.bank.app.transfer.application.usecase;

import com.bank.app.common.application.port.in.ReadOnlyUseCase;
import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferReportTotalsResponse;
import com.bank.app.transfer.application.port.in.GenerateTransferReportTotalsQuery;
import com.bank.app.transfer.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.service.TransferAuthorizationService;

import java.time.LocalDateTime;
import java.util.Objects;

@ReadOnlyUseCase
public class GenerateTransferReportTotalsQueryImpl implements GenerateTransferReportTotalsQuery {

    private final LoadTransferPort loadTransferPort;
    private final TransferAuthorizationService transferAuthorizationService;

    public GenerateTransferReportTotalsQueryImpl(LoadTransferPort loadTransferPort,
                                                TransferAuthorizationService transferAuthorizationService) {
        this.loadTransferPort = loadTransferPort;
        this.transferAuthorizationService = transferAuthorizationService;
    }

    @Override
    public TransferReportTotalsResponse execute(ReportCriteria criteria) {
        Objects.requireNonNull(criteria, "Criteria must not be null");
        // Date-range invariants live in ReportCriteria's compact constructor.
        Long accountId = criteria.accountId();
        LocalDateTime startDate = criteria.startDate();
        LocalDateTime endDate = criteria.endDate();

        AccountInfo account = transferAuthorizationService.authorizeAccountAccess(accountId,
                "You are not authorized to generate a report for this account.");

        LoadTransferPort.ReportTotals totals =
                loadTransferPort.summarizeRange(accountId, startDate, endDate);

        return new TransferReportTotalsResponse(accountId, totals.count(), totals.volume(), account.currency());
    }
}
