package com.bank.app.transfer.infrastructure.decorator;

import com.bank.app.transfer.application.dto.TransferDetailResponse;
import com.bank.app.transfer.application.port.in.GetTransferDetailQuery;
import com.bank.app.transfer.application.port.out.AccountOperationPort;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.usecase.GetTransferDetailUseCaseImpl;
import com.bank.app.common.security.port.out.SecurityContextPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class GetTransferDetailUseCaseDecorator implements GetTransferDetailQuery {

    private final GetTransferDetailUseCaseImpl delegate;

    public GetTransferDetailUseCaseDecorator(
            LoadTransferPort loadTransferPort,
            AccountOperationPort accountOperationPort,
            SecurityContextPort securityContextPort) {
        this.delegate = new GetTransferDetailUseCaseImpl(loadTransferPort, accountOperationPort, securityContextPort);
    }

    @Override
    public TransferDetailResponse execute(Long transferId) {
        return delegate.execute(transferId);
    }
}
