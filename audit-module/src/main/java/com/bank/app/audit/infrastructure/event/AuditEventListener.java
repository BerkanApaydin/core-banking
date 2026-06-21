package com.bank.app.audit.infrastructure.event;

import com.bank.app.account.domain.AccountCreatedEvent;
import com.bank.app.audit.application.AuditLogger;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.transfer.domain.TransferCancelledEvent;
import com.bank.app.transfer.domain.TransferCompletedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AuditEventListener {

    private final AuditLogger auditLogger;

    public AuditEventListener(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountCreated(AccountCreatedEvent event) {
        auditLogger.log(
            AuditAction.ACCOUNT_CREATED,
            String.format("Yeni hesap oluşturuldu. ID: %d, IBAN: %s, Kullanıcı ID: %d, Bakiye: %s %s",
                event.getAccountId(), event.getIban(), event.getUserId(),
                event.getBalance().amount(), event.getBalance().currency())
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransferCompleted(TransferCompletedEvent event) {
        auditLogger.log(
            AuditAction.TRANSFER_EXECUTED,
            String.format("Para transferi gerçekleştirildi. Transfer ID: %d, Tutar: %s %s",
                event.getTransferId(), event.getAmount().amount(), event.getAmount().currency().name())
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransferCancelled(TransferCancelledEvent event) {
        auditLogger.log(
            AuditAction.TRANSFER_CANCELLED,
            String.format("Transfer iptal edildi. Transfer ID: %d, Gönderici Hesaba Geri Yüklenen: %s %s, Alıcı Hesaptan Düşülen: %s %s",
                event.getTransferId(), event.getAmount().amount(), event.getAmount().currency().name(),
                event.getAmount().amount(), event.getAmount().currency().name())
        );
    }
}
