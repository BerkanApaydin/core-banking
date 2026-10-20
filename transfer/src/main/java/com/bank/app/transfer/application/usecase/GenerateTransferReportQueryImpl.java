package com.bank.app.transfer.application.usecase;

import com.bank.app.common.application.port.in.ReadOnlyUseCase;
import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferReportResponse;
import com.bank.app.transfer.application.port.in.GenerateTransferReportQuery;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.service.AbstractTransferReportQuery;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
import com.bank.app.transfer.application.service.TransferViewEnricher;

@ReadOnlyUseCase
public class GenerateTransferReportQueryImpl extends AbstractTransferReportQuery
        implements GenerateTransferReportQuery {

    public GenerateTransferReportQueryImpl(LoadTransferPort loadTransferPort,
            TransferViewEnricher viewEnricher,
            TransferAuthorizationService transferAuthorizationService,
            int maxPageSize) {
        super(loadTransferPort, viewEnricher, transferAuthorizationService, maxPageSize);
    }

    @Override
    public TransferReportResponse execute(ReportCriteria criteria) {
        return execute(criteria, false);
    }
}
