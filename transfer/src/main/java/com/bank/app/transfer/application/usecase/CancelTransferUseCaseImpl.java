package com.bank.app.transfer.application.usecase;

import com.bank.app.common.application.UseCase;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.common.domain.event.DomainEvent;
import com.bank.app.common.domain.event.DomainEventProvider;
import com.bank.app.transfer.application.exception.TransferNotFoundException;
import com.bank.app.transfer.application.port.in.CancelTransferUseCase;
import com.bank.app.common.application.port.out.AccountAclPort;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
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
    private final TransferAuthorizationService transferAuthorizationService;
    private final int cancellationWindowHours;

    public CancelTransferUseCaseImpl(LoadTransferPort loadTransferPort,
                                 SaveTransferPort saveTransferPort,
                                 AccountAclPort accountAclPort,
                                 EventPublisherPort eventPublisherPort,
                                 TransferAuthorizationService transferAuthorizationService,
                                 int cancellationWindowHours) {
        this.loadTransferPort = loadTransferPort;
        this.saveTransferPort = saveTransferPort;
        this.accountAclPort = accountAclPort;
        this.eventPublisherPort = eventPublisherPort;
        this.transferAuthorizationService = transferAuthorizationService;
        this.cancellationWindowHours = cancellationWindowHours;
    }

    @Override
    public void execute(Long transferId) {
        Objects.requireNonNull(transferId, "Transfer ID must not be null");
        Transfer transfer = loadTransferPort.findByIdWithLock(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));

        Long senderAccountId = transfer.getSenderAccountId();
        Long receiverAccountId = transfer.getReceiverAccountId();

        transferAuthorizationService.authorizeByAccountId(senderAccountId);

        transfer.cancel(Clock.systemDefaultZone(), cancellationWindowHours);

        accountAclPort.reverseBalancesForCancellation(senderAccountId, receiverAccountId, transfer.getAmount());

        saveTransferPort.save(transfer);

        log.info("Transfer cancelled: id={}, amount={}",
            transfer.getId(), transfer.getAmount());

        publishEvents(transfer);
        eventPublisherPort.publish(new AuditEvent("TRANSFER_CANCELLED",
            String.format("Transfer cancelled. Transfer ID: %d, Amount: %s %s",
                transfer.getId(), transfer.getAmount().amount(), transfer.getAmount().currency()),
            java.time.LocalDateTime.now()));
    }

    private void publishEvents(DomainEventProvider provider) {
        List<DomainEvent> events = List.copyOf(provider.getDomainEvents());
        provider.clearDomainEvents();
        for (DomainEvent event : events) {
            eventPublisherPort.publish(Objects.requireNonNull(event, "Domain event must not be null"));
        }
    }
}
