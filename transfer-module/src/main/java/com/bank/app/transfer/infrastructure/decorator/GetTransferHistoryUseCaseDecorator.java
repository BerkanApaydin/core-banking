package com.bank.app.transfer.infrastructure.decorator;

import com.bank.app.transfer.application.dto.PagedResponse;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.in.GetTransferHistoryPort;
import com.bank.app.transfer.application.port.out.AccountOperationPort;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.usecase.GetTransferHistoryUseCase;
import com.bank.app.common.security.port.out.SecurityContextPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class GetTransferHistoryUseCaseDecorator implements GetTransferHistoryPort {

    private final GetTransferHistoryUseCase delegate;

    public GetTransferHistoryUseCaseDecorator(
            LoadTransferPort loadTransferPort,
            AccountOperationPort accountOperationPort,
            SecurityContextPort securityContextPort) {
        this.delegate = new GetTransferHistoryUseCase(loadTransferPort, accountOperationPort, securityContextPort);
    }

    @Override
    public PagedResponse<TransferResponse> execute(Long accountId) {
        return delegate.execute(accountId);
    }

    @Override
    public PagedResponse<TransferResponse> execute(Long accountId, int page, int size) {
        return delegate.execute(accountId, page, size);
    }
}
