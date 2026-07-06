package com.bank.app.transfer.application.usecase;

import com.bank.app.common.application.port.in.TransactionalUseCase;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.event.DomainEvent;
import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.in.PlaceTransferUseCase;
import com.bank.app.common.application.port.out.AccountAclPort;
import com.bank.app.common.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferDomainService;
import com.bank.app.transfer.domain.TransferParticipants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Objects;

@TransactionalUseCase
public class PlaceTransferUseCaseImpl implements PlaceTransferUseCase {

    private static final Logger log = LoggerFactory.getLogger(PlaceTransferUseCaseImpl.class);

    private final AccountAclPort accountAclPort;
    private final SaveTransferPort saveTransferPort;
    private final TransferDomainService transferDomainService;
    private final TransferAuthorizationService transferAuthorizationService;
    private final DomainEventPublisherService domainEventPublisherService;

    public PlaceTransferUseCaseImpl(AccountAclPort accountAclPort,
            SaveTransferPort saveTransferPort,
            TransferDomainService transferDomainService,
            TransferAuthorizationService transferAuthorizationService,
            DomainEventPublisherService domainEventPublisherService) {
        this.accountAclPort = accountAclPort;
        this.saveTransferPort = saveTransferPort;
        this.transferDomainService = transferDomainService;
        this.transferAuthorizationService = transferAuthorizationService;
        this.domainEventPublisherService = domainEventPublisherService;
    }

    @Override
    public TransferResponse execute(TransferRequest request) {
        Objects.requireNonNull(request, "Request must not be null");

        String senderIban = Iban.normalize(request.senderIban());
        String receiverIban = Iban.normalize(request.receiverIban());

        AccountInfo senderInfo = transferAuthorizationService.authorizeSender(senderIban);
        AccountInfo receiverInfo = transferAuthorizationService.getReceiverInfo(receiverIban);

        Money amount = new Money(request.amount(), request.currency());

        Transfer transfer = createAndValidateTransfer(senderInfo, receiverInfo, senderIban,
                receiverIban, amount);

        Transfer savedTransfer = saveTransferPort.save(transfer);

        List<DomainEvent> accountEvents = accountAclPort.debitAndCredit(senderInfo.id(), receiverInfo.id(), amount);

        savedTransfer.complete();
        saveTransferPort.save(savedTransfer);

        accountEvents.forEach(domainEventPublisherService::publish);
        domainEventPublisherService.publishEvents(savedTransfer);

        log.info("Transfer completed: id={}, senderId={}, receiverId={}",
            savedTransfer.getId(), savedTransfer.getSenderAccountId(),
            savedTransfer.getReceiverAccountId());

        return TransferResponse.from(savedTransfer, senderIban, receiverIban);
    }

    private Transfer createAndValidateTransfer(AccountInfo sender, AccountInfo receiver, String senderIban,
            String receiverIban, Money amount) {
        TransferParticipants participants = new TransferParticipants(
                sender.id(), senderIban, Currency.valueOf(sender.currency()),
                receiver.id(), receiverIban, Currency.valueOf(receiver.currency()));
        return transferDomainService.validateAndCreateTransfer(participants, amount);
    }

}
