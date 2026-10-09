package com.bank.app.transfer.application.usecase;

import com.bank.app.common.application.port.in.TransactionalUseCase;
import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.in.PlaceTransferUseCase;
import com.bank.app.transfer.application.port.out.AccountAclPort;
import com.bank.app.transfer.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferDomainService;
import com.bank.app.transfer.domain.TransferParticipants;
import com.bank.app.common.application.port.out.ClockProviderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Objects;
import java.time.LocalDateTime;

@TransactionalUseCase
public class PlaceTransferUseCaseImpl implements PlaceTransferUseCase {

    private static final Logger log = LoggerFactory.getLogger(PlaceTransferUseCaseImpl.class);

    private final AccountAclPort accountAclPort;
    private final SaveTransferPort saveTransferPort;
    private final TransferDomainService transferDomainService;
    private final TransferAuthorizationService transferAuthorizationService;
    private final DomainEventPublisherService domainEventPublisherService;
    private final ClockProviderPort clockProvider;
    private final AuditEventPort auditEventPort;

    public PlaceTransferUseCaseImpl(AccountAclPort accountAclPort,
            SaveTransferPort saveTransferPort,
            TransferDomainService transferDomainService,
            TransferAuthorizationService transferAuthorizationService,
            DomainEventPublisherService domainEventPublisherService,
            ClockProviderPort clockProvider,
            AuditEventPort auditEventPort) {
        this.accountAclPort = accountAclPort;
        this.saveTransferPort = saveTransferPort;
        this.transferDomainService = transferDomainService;
        this.transferAuthorizationService = transferAuthorizationService;
        this.domainEventPublisherService = domainEventPublisherService;
        this.clockProvider = clockProvider;
        this.auditEventPort = auditEventPort;
    }

    @Override
    public TransferResponse execute(TransferRequest request) {
        Objects.requireNonNull(request, "Request must not be null");

        String senderIban = Iban.normalize(request.senderIban());
        String receiverIban = Iban.normalize(request.receiverIban());

        AccountInfo senderInfo = transferAuthorizationService.authorizeSender(senderIban);
        AccountInfo receiverInfo = transferAuthorizationService.getReceiverInfo(receiverIban);

        Money amount = Money.ofTransferAmount(request.amount(), request.currency());

        Transfer transfer = createAndValidateTransfer(senderInfo, receiverInfo, senderIban,
                receiverIban, amount);

        // PENDING save first: the ID is needed for the COMPLETED domain event
        // (TransferCompletedEvent requires non-null transferId). Both saves join
        // this use-case's local transaction (REQUIRED): atomic in the modular
        // monolith (single DataSource). If the Account context ever moves to a
        // separate service, this needs a Saga.
        Transfer pendingTransfer = saveTransferPort.save(transfer);
        accountAclPort.debitAndCredit(senderInfo.id(), receiverInfo.id(), amount);

        pendingTransfer.complete(clockProvider.clock());
        // Perf-1 note: the bulk UPDATE path returns a fresh aggregate without
        // domain events (BaseAggregateRoot.registerEvent is protected), so the
        // COMPLETED event is published from pendingTransfer — which carries the
        // event with the assigned ID — not from the reloaded copy. Publish
        // AFTER the second save so a publish failure still leaves both writes
        // behind (existing failure-handling contract: no compensation).
        Transfer savedTransfer = saveTransferPort.save(pendingTransfer);
        domainEventPublisherService.publishEvents(pendingTransfer);
        auditEventPort.publish(new AuditEvent(AuditEvent.TRANSFER_EXECUTED,
                "Transfer completed. Transfer ID: " + savedTransfer.getId(),
                LocalDateTime.now(clockProvider.clock()),
                transferAuthorizationService.getCurrentUsername(),
                senderInfo.userId()));

        log.info("Transfer completed: id={}, senderId={}, receiverId={}",
            savedTransfer.getId(), savedTransfer.getSenderAccountId(),
            savedTransfer.getReceiverAccountId());

        return TransferResponse.from(savedTransfer, senderIban, receiverIban);
    }

    private Transfer createAndValidateTransfer(AccountInfo sender, AccountInfo receiver, String senderIban,
            String receiverIban, Money amount) {
        // AccountInfo ids are already AccountId: no Long ever crosses this
        // seam, so sender/receiver order cannot be silently swapped.
        TransferParticipants participants = new TransferParticipants(
                sender.id(), senderIban, Currency.fromCode(sender.currency()),
                receiver.id(), receiverIban, Currency.fromCode(receiver.currency()));
        return transferDomainService.validateAndCreateTransfer(participants, amount, clockProvider.clock());
    }

}
