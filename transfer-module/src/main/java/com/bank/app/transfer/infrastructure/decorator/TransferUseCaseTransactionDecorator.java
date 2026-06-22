package com.bank.app.transfer.infrastructure.decorator;

import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.in.CancelTransferUseCase;
import com.bank.app.transfer.application.port.in.PlaceTransferUseCase;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;

public class TransferUseCaseTransactionDecorator implements PlaceTransferUseCase, CancelTransferUseCase {

    private final PlaceTransferUseCase delegate;
    private final CancelTransferUseCase cancelDelegate;

    public TransferUseCaseTransactionDecorator(PlaceTransferUseCase delegate, CancelTransferUseCase cancelDelegate) {
        this.delegate = delegate;
        this.cancelDelegate = cancelDelegate;
    }

    @Override
    @Transactional
    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public TransferResponse execute(TransferRequest request) {
        return delegate.execute(request);
    }

    @Override
    @Transactional
    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public void execute(Long transferId) {
        cancelDelegate.execute(transferId);
    }
}
