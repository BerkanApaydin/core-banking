package com.bank.app.transfer.application.usecase;

import com.bank.app.common.application.DomainEventPublisher;
import com.bank.app.common.application.UseCase;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.application.port.out.security.SecurityContextPort;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.in.PlaceTransferUseCase;
import com.bank.app.common.application.port.out.AccountAclPort;
import com.bank.app.common.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferDomainService;
import com.bank.app.transfer.domain.TransferParticipants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.Objects;

@UseCase
public class PlaceTransferUseCaseImpl implements PlaceTransferUseCase {

    private static final Logger log = LoggerFactory.getLogger(PlaceTransferUseCaseImpl.class);

    private final AccountAclPort accountAclPort;
    private final SaveTransferPort saveTransferPort;
    private final EventPublisherPort eventPublisherPort;
    private final TransferDomainService transferDomainService;
    private final SecurityContextPort securityContextPort;

    public PlaceTransferUseCaseImpl(AccountAclPort accountAclPort,
            SaveTransferPort saveTransferPort,
            EventPublisherPort eventPublisherPort,
            TransferDomainService transferDomainService,
            SecurityContextPort securityContextPort) {
        this.accountAclPort = accountAclPort;
        this.saveTransferPort = saveTransferPort;
        this.eventPublisherPort = eventPublisherPort;
        this.transferDomainService = transferDomainService;
        this.securityContextPort = securityContextPort;
    }

    @Override
    public TransferResponse execute(TransferRequest request) {
        Objects.requireNonNull(request, "Request null olamaz");

        String senderIban = Iban.normalize(request.senderIban());
        String receiverIban = Iban.normalize(request.receiverIban());

        AccountInfo senderInfo = accountAclPort.getAccountInfoForTransfer(senderIban);
        AccountInfo receiverInfo = accountAclPort.getAccountInfoForTransfer(receiverIban);

        securityContextPort.checkUserAuthorization(senderInfo.userId(),
                "Bu hesaptan transfer yapmaya yetkiniz yok.");

        Money amount = new Money(request.amount(), request.currency());

        Transfer transfer = createAndValidateTransfer(senderInfo, receiverInfo, senderIban,
                receiverIban, amount);

        Transfer savedTransfer = saveTransferPort.save(transfer);

        try {
            accountAclPort.debitAndCredit(senderInfo.id(), receiverInfo.id(), amount);

            savedTransfer.complete();
            Transfer completedTransfer = saveTransferPort.save(savedTransfer);

            DomainEventPublisher.publishEvents(savedTransfer, eventPublisherPort);
            eventPublisherPort.publish(new AuditEvent("TRANSFER_EXECUTED",
                String.format("Para transferi gerçekleştirildi. Transfer ID: %d, Tutar: %s %s",
                    completedTransfer.getId(), completedTransfer.getAmount().amount(),
                    completedTransfer.getAmount().currency()),
                LocalDateTime.now()));

            log.info("Transfer completed: id={}, senderId={}, receiverId={}, amount={} {}",
                completedTransfer.getId(), completedTransfer.getSenderAccountId(),
                completedTransfer.getReceiverAccountId(),
                completedTransfer.getAmount().amount(), completedTransfer.getAmount().currency());

            return TransferResponse.from(completedTransfer, senderIban, receiverIban);
        } catch (Exception e) {
            savedTransfer.markFailed();
            saveTransferPort.save(savedTransfer);
            log.error("Transfer failed after debit, marked as FAILED: id={}", savedTransfer.getId(), e);
            throw e;
        }
    }

    private Transfer createAndValidateTransfer(AccountInfo sender, AccountInfo receiver, String senderIban,
            String receiverIban, Money amount) {
        TransferParticipants participants = new TransferParticipants(
                sender.id(), senderIban, Currency.valueOf(sender.currency()),
                receiver.id(), receiverIban, Currency.valueOf(receiver.currency()));
        return transferDomainService.validateAndCreateTransfer(participants, amount);
    }

}
