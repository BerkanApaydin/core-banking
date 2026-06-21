package com.bank.app.transfer.infrastructure.decorator;

import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.in.PlaceTransferUseCase;
import com.bank.app.transfer.application.port.out.AccountOperationPort;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.application.usecase.PlaceTransferUseCaseImpl;
import com.bank.app.transfer.domain.TransferDomainService;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class PlaceTransferUseCaseDecorator implements PlaceTransferUseCase {

    private final PlaceTransferUseCaseImpl delegate;

    public PlaceTransferUseCaseDecorator(
            AccountOperationPort accountOperationPort,
            SaveTransferPort saveTransferPort,
            EventPublisherPort eventPublisherPort,
            TransferDomainService transferDomainService) {
        this.delegate = new PlaceTransferUseCaseImpl(
                accountOperationPort, saveTransferPort, eventPublisherPort, transferDomainService);
    }

    @Override
    @Retryable(
            retryFor = ConcurrencyFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public TransferResponse execute(TransferRequest request) {
        return delegate.execute(request);
    }
}
