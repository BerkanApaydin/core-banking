package com.bank.app.transfer.application.usecase;

import com.bank.app.common.application.UseCase;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.application.port.out.security.SecurityContextPort;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.common.domain.event.DomainEvent;
import com.bank.app.common.domain.event.DomainEventProvider;
import com.bank.app.transfer.application.exception.TransferNotFoundException;
import com.bank.app.transfer.application.port.in.CancelTransferUseCase;
import com.bank.app.common.application.port.out.AccountAclPort;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.domain.Transfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

@UseCase
public class CancelTransferUseCaseImpl implements CancelTransferUseCase {

    private static final Logger log = LoggerFactory.getLogger(CancelTransferUseCaseImpl.class);

    private final LoadTransferPort loadTransferPort;
    private final SaveTransferPort saveTransferPort;
    private final AccountAclPort accountAclPort;
    private final EventPublisherPort eventPublisherPort;
    private final SecurityContextPort securityContextPort;
    private final int cancellationWindowHours;

    public CancelTransferUseCaseImpl(LoadTransferPort loadTransferPort,
                                 SaveTransferPort saveTransferPort,
                                 AccountAclPort accountAclPort,
                                 EventPublisherPort eventPublisherPort,
                                 SecurityContextPort securityContextPort,
                                 int cancellationWindowHours) {
        this.loadTransferPort = loadTransferPort;
        this.saveTransferPort = saveTransferPort;
        this.accountAclPort = accountAclPort;
        this.eventPublisherPort = eventPublisherPort;
        this.securityContextPort = securityContextPort;
        this.cancellationWindowHours = cancellationWindowHours;
    }

    @Override
    public void execute(Long transferId) {
        Objects.requireNonNull(transferId, "Transfer ID null olamaz");
        Transfer transfer = loadTransferPort.findByIdWithLock(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));

        Long senderAccountId = transfer.getSenderAccountId();
        Long receiverAccountId = transfer.getReceiverAccountId();

        Long senderUserId = accountAclPort.getAccountInfo(senderAccountId).userId();
        securityContextPort.checkUserAuthorization(senderUserId, "Bu transferi iptal etmeye yetkiniz yok.");

        transfer.cancel(Clock.systemDefaultZone(), cancellationWindowHours);

        accountAclPort.reverseBalancesForCancellation(senderAccountId, receiverAccountId, transfer.getAmount());

        saveTransferPort.save(transfer);

        log.info("Transfer iptal edildi: id={}, amount={}",
            transfer.getId(), transfer.getAmount());

        publishEvents(transfer);
        eventPublisherPort.publish(new AuditEvent("TRANSFER_CANCELLED",
            String.format("Transfer iptal edildi. Transfer ID: %d, Tutar: %s %s",
                transfer.getId(), transfer.getAmount().amount(), transfer.getAmount().currency()),
            java.time.LocalDateTime.now()));
    }

    private void publishEvents(DomainEventProvider provider) {
        List<DomainEvent> events = List.copyOf(provider.getDomainEvents());
        provider.clearDomainEvents();
        for (DomainEvent event : events) {
            eventPublisherPort.publish(Objects.requireNonNull(event, "Domain event null olamaz"));
        }
    }
}
