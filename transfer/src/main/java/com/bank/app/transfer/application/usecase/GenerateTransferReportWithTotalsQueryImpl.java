package com.bank.app.transfer.application.usecase;

import com.bank.app.common.application.port.in.ReadOnlyUseCase;
import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferReportResponse;
import com.bank.app.transfer.application.port.in.GenerateTransferReportWithTotalsQuery;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.service.AbstractTransferReportQuery;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
import com.bank.app.transfer.application.service.TransferViewEnricher;

/**
 * API-2: items + whole-range totals in one use-case call (single
 * authorization, same read transaction). Eliminates the two-round-trip
 * inconsistency where page and totals were computed at different instants.
 */
@ReadOnlyUseCase
public class GenerateTransferReportWithTotalsQueryImpl extends AbstractTransferReportQuery
        implements GenerateTransferReportWithTotalsQuery {

    public GenerateTransferReportWithTotalsQueryImpl(LoadTransferPort loadTransferPort,
            TransferViewEnricher viewEnricher,
            TransferAuthorizationService transferAuthorizationService,
            int maxPageSize) {
        super(loadTransferPort, viewEnricher, transferAuthorizationService, maxPageSize);
    }

    @Override
    public TransferReportResponse execute(ReportCriteria criteria) {
        return execute(criteria, true);
    }
}
