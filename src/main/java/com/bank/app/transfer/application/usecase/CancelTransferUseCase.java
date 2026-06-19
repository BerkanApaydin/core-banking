package com.bank.app.transfer.application.usecase;

import com.bank.app.transfer.application.port.AccountOperationsPort;
import com.bank.app.audit.application.AuditService;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.transfer.exception.TransferNotFoundException;
import com.bank.app.common.security.port.SecurityContextPort;
import com.bank.app.transfer.application.port.LoadTransferPort;
import com.bank.app.transfer.application.port.SaveTransferPort;
import com.bank.app.transfer.domain.Transfer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@Transactional
public class CancelTransferUseCase {

    private final LoadTransferPort loadTransferPort;
    private final SaveTransferPort saveTransferPort;
    private final AccountOperationsPort accountOperationsPort;
    private final AuditService auditService;
    private final SecurityContextPort securityContextPort;
    private final int cancellationWindowHours;

    public CancelTransferUseCase(LoadTransferPort loadTransferPort,
                                 SaveTransferPort saveTransferPort,
                                 AccountOperationsPort accountOperationsPort,
                                 AuditService auditService,
                                 SecurityContextPort securityContextPort,
                                 @Value("${app.transfer.cancellation-window-hours}") int cancellationWindowHours) {
        this.loadTransferPort = loadTransferPort;
        this.saveTransferPort = saveTransferPort;
        this.accountOperationsPort = accountOperationsPort;
        this.auditService = auditService;
        this.securityContextPort = securityContextPort;
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

        Long senderAccountId = transfer.getSenderAccountId();
        Long receiverAccountId = transfer.getReceiverAccountId();

        // Auth check before domain mutation (fail-fast)
        Long senderUserId = accountOperationsPort.getAccountInfo(senderAccountId).userId();
        securityContextPort.checkUserAuthorization(senderUserId, "Bu transferi iptal etmeye yetkiniz yok.");

        transfer.cancel(cancellationWindowHours);

        accountOperationsPort.reverseBalancesForCancellation(senderAccountId, receiverAccountId, transfer.getAmount());

        saveTransferPort.save(transfer);

        auditService.log(
            AuditAction.TRANSFER_CANCELLED,
            String.format("Transfer iptal edildi. Transfer ID: %d, Gönderici Hesaba Geri Yüklenen: %s %s, Alıcı Hesaptan Düşülen: %s %s",
                transfer.getId(), transfer.getAmount().amount(), transfer.getAmount().currency().name(),
                transfer.getAmount().amount(), transfer.getAmount().currency().name())
        );
    }
}
