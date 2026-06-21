package com.bank.app.transfer.infrastructure.decorator;

import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.security.port.out.SecurityContextPort;
import com.bank.app.transfer.application.port.in.CancelTransferUseCase;
import com.bank.app.transfer.application.port.out.AccountOperationPort;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.application.usecase.CancelTransferUseCaseImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class CancelTransferUseCaseDecorator implements CancelTransferUseCase {

    private final CancelTransferUseCaseImpl delegate;

    public CancelTransferUseCaseDecorator(
            LoadTransferPort loadTransferPort,
            SaveTransferPort saveTransferPort,
            AccountOperationPort accountOperationPort,
            EventPublisherPort eventPublisherPort,
            SecurityContextPort securityContextPort,
            @Value("${app.transfer.cancellation-window-hours}") int cancellationWindowHours) {
        this.delegate = new CancelTransferUseCaseImpl(
                loadTransferPort, saveTransferPort, accountOperationPort,
                eventPublisherPort, securityContextPort, cancellationWindowHours);
    }

    @Override
    @Retryable(
            retryFor = ConcurrencyFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public void execute(Long transferId) {
        delegate.execute(transferId);
    }
}
