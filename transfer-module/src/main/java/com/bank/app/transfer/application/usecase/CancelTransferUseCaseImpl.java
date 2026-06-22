package com.bank.app.transfer.application.usecase;

import com.bank.app.transfer.application.port.out.AccountOperationPort;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.transfer.domain.TransferCancelledEvent;
import com.bank.app.transfer.application.exception.TransferNotFoundException;
import com.bank.app.common.security.port.out.SecurityContextPort;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.application.port.in.CancelTransferUseCase;
import com.bank.app.transfer.domain.Transfer;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

public class CancelTransferUseCaseImpl implements CancelTransferUseCase {

    private final LoadTransferPort loadTransferPort;
    private final SaveTransferPort saveTransferPort;
    private final AccountOperationPort accountOperationPort;
    private final EventPublisherPort eventPublisherPort;
    private final SecurityContextPort securityContextPort;
    private final int cancellationWindowHours;

    public CancelTransferUseCaseImpl(LoadTransferPort loadTransferPort,
                                 SaveTransferPort saveTransferPort,
                                 AccountOperationPort accountOperationPort,
                                 EventPublisherPort eventPublisherPort,
                                 SecurityContextPort securityContextPort,
                                 int cancellationWindowHours) {
        this.loadTransferPort = loadTransferPort;
        this.saveTransferPort = saveTransferPort;
        this.accountOperationPort = accountOperationPort;
        this.eventPublisherPort = eventPublisherPort;
        this.securityContextPort = securityContextPort;
        this.cancellationWindowHours = cancellationWindowHours;
    }

    @Override
    @Transactional
    @Retryable(
            retryFor = ConcurrencyFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public void execute(Long transferId) {
        Objects.requireNonNull(transferId, "Transfer ID null olamaz");
        Transfer transfer = loadTransferPort.findByIdWithLock(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));

        Long senderAccountId = transfer.getSenderAccountId();
        Long receiverAccountId = transfer.getReceiverAccountId();

        Long senderUserId = accountOperationPort.getAccountInfo(senderAccountId).userId();
        securityContextPort.checkUserAuthorization(senderUserId, "Bu transferi iptal etmeye yetkiniz yok.");

        transfer.cancel(cancellationWindowHours);

        accountOperationPort.reverseBalancesForCancellation(senderAccountId, receiverAccountId, transfer.getAmount());

        saveTransferPort.save(transfer);

        eventPublisherPort.publish(new TransferCancelledEvent(
            transfer.getId(), transfer.getSenderAccountId(), transfer.getReceiverAccountId(), transfer.getAmount()
        ));
    }
}
