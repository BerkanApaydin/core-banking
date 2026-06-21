package com.bank.app.transfer.application.usecase;

import com.bank.app.common.domain.Money;
import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.out.AccountOperationPort;
import com.bank.app.transfer.application.port.out.AccountOperationPort.AccountInfo;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferCompletedEvent;
import com.bank.app.transfer.application.port.in.PlaceTransferUseCase;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.transfer.domain.TransferDomainService;
import com.bank.app.transfer.domain.TransferParticipants;
import java.util.Objects;

public class PlaceTransferUseCaseImpl implements PlaceTransferUseCase {

    private final AccountOperationPort accountOperationPort;
    private final SaveTransferPort saveTransferPort;
    private final EventPublisherPort eventPublisherPort;
    private final TransferDomainService transferDomainService;

    public PlaceTransferUseCaseImpl(AccountOperationPort accountOperationPort,
            SaveTransferPort saveTransferPort,
            EventPublisherPort eventPublisherPort,
            TransferDomainService transferDomainService) {
        this.accountOperationPort = accountOperationPort;
        this.saveTransferPort = saveTransferPort;
        this.eventPublisherPort = eventPublisherPort;
        this.transferDomainService = transferDomainService;
    }

    public TransferResponse execute(TransferRequest request) {
        Objects.requireNonNull(request, "Request null olamaz");

        String senderIban = normalizeIban(request.senderIban());
        String receiverIban = normalizeIban(request.receiverIban());

        AccountInfo senderInfo = accountOperationPort.getAccountInfoForTransfer(senderIban);
        AccountInfo receiverInfo = accountOperationPort.getAccountInfoForTransfer(receiverIban);

        Money amount = new Money(request.amount(), request.currency());

        Transfer transfer = createAndValidateTransfer(senderInfo, receiverInfo, senderIban,
                receiverIban, amount);

        accountOperationPort.debitAndCredit(senderInfo.id(), receiverInfo.id(), amount);

        transfer.complete();

        Transfer completedTransfer = saveTransferPort.save(transfer);

        publishEvent(completedTransfer);

        return TransferResponse.from(completedTransfer, senderIban, receiverIban);
    }

    private static String normalizeIban(String iban) {
        return iban.replaceAll("\\s", "").toUpperCase();
    }

    private Transfer createAndValidateTransfer(AccountInfo sender, AccountInfo receiver, String senderIban,
            String receiverIban, Money amount) {
        TransferParticipants participants = new TransferParticipants(
                sender.id(), senderIban, Money.Currency.valueOf(sender.currency()),
                receiver.id(), receiverIban, Money.Currency.valueOf(receiver.currency()));
        return transferDomainService.validateAndCreateTransfer(participants, amount);
    }

    private void publishEvent(Transfer savedTransfer) {
        eventPublisherPort.publish(new TransferCompletedEvent(
                savedTransfer.getId(),
                savedTransfer.getSenderAccountId(),
                savedTransfer.getReceiverAccountId(),
                savedTransfer.getAmount(),
                savedTransfer.getStatus()));
    }
}
