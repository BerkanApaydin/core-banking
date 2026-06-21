package com.bank.app.transfer.infrastructure.decorator;

import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferReportResponse;
import com.bank.app.transfer.application.port.in.GenerateTransferReportPort;
import com.bank.app.transfer.application.port.out.AccountOperationPort;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.usecase.GenerateTransferReportUseCase;
import com.bank.app.common.security.port.out.SecurityContextPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class GenerateTransferReportUseCaseDecorator implements GenerateTransferReportPort {

    private final GenerateTransferReportUseCase delegate;

    public GenerateTransferReportUseCaseDecorator(
            LoadTransferPort loadTransferPort,
            AccountOperationPort accountOperationPort,
            SecurityContextPort securityContextPort) {
        this.delegate = new GenerateTransferReportUseCase(loadTransferPort, accountOperationPort, securityContextPort);
    }

    @Override
    public TransferReportResponse execute(ReportCriteria criteria) {
        return delegate.execute(criteria);
    }
}
