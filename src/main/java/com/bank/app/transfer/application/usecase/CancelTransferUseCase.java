package com.bank.app.transfer.application.usecase;

import com.bank.app.account.application.usecase.AccountInternalService;
import com.bank.app.audit.application.service.AuditService;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.common.exception.TransferNotFoundException;
import com.bank.app.transfer.application.port.LoadTransferPort;
import com.bank.app.transfer.application.port.SaveTransferPort;
import com.bank.app.transfer.domain.Transfer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.dao.ConcurrencyFailureException;

import java.util.Objects;

@Service
@Transactional
public class CancelTransferUseCase {

    private final LoadTransferPort loadTransferPort;
    private final SaveTransferPort saveTransferPort;
    private final AccountInternalService accountInternalService;
    private final AuditService auditService;
    private final int cancellationWindowHours;

    public CancelTransferUseCase(LoadTransferPort loadTransferPort, 
                                 SaveTransferPort saveTransferPort,
                                 AccountInternalService accountInternalService,
                                 AuditService auditService,
                                 @Value("${app.transfer.cancellation-window-hours}") int cancellationWindowHours) {
        this.loadTransferPort = loadTransferPort;
        this.saveTransferPort = saveTransferPort;
        this.accountInternalService = accountInternalService;
        this.auditService = auditService;
        this.cancellationWindowHours = cancellationWindowHours;
    }

    @Retryable(
            retryFor = ConcurrencyFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public void execute(Long transferId) {
        Objects.requireNonNull(transferId, "Transfer ID null olamaz");
        Transfer transfer = loadTransferPort.findByIdWithLock(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));

        Long senderAccountId = Objects.requireNonNull(transfer.getSenderAccountId(), "Sender account ID null olamaz");
        Long receiverAccountId = Objects.requireNonNull(transfer.getReceiverAccountId(), "Receiver account ID null olamaz");

        if (senderAccountId.equals(receiverAccountId)) {
            throw new IllegalArgumentException("Gönderici ve alıcı hesap aynı olamaz.");
        }

        // Delegate reversing balances, locking, and authorization check to the Account module
        accountInternalService.reverseBalancesForCancellation(senderAccountId, receiverAccountId, transfer.getAmount());

        // Validate and update status in the domain model
        transfer.cancel(cancellationWindowHours);

        // Persist Transfer status update
        saveTransferPort.save(transfer);

        // Audit log
        auditService.log(
            AuditAction.TRANSFER_CANCELLED,
            String.format("Transfer iptal edildi. Transfer ID: %d, Gönderici Hesaba Geri Yüklenen: %s %s, Alıcı Hesaptan Düşülen: %s %s",
                transfer.getId(), transfer.getAmount().amount(), transfer.getAmount().currency().name(),
                transfer.getAmount().amount(), transfer.getAmount().currency().name())
        );
    }
}
