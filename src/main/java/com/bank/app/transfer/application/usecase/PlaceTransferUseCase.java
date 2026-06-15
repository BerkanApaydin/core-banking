package com.bank.app.transfer.application.usecase;

import com.bank.app.account.application.usecase.AccountInternalService;
import com.bank.app.account.application.usecase.AccountInternalService.AccountInfo;
import com.bank.app.audit.application.service.AuditService;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.common.domain.Money;
import com.bank.app.common.exception.AccountNotActiveException;
import com.bank.app.common.exception.SameAccountTransferException;
import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.SaveTransferPort;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferCompletedEvent;
import com.bank.app.transfer.domain.TransferDomainService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.dao.ConcurrencyFailureException;

import java.util.Objects;

@Service
@Transactional
public class PlaceTransferUseCase {

    private final AccountInternalService accountInternalService;
    private final SaveTransferPort saveTransferPort;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransferDomainService transferDomainService;

    public PlaceTransferUseCase(AccountInternalService accountInternalService,
            SaveTransferPort saveTransferPort,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher,
            TransferDomainService transferDomainService) {
        this.accountInternalService = accountInternalService;
        this.saveTransferPort = saveTransferPort;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.transferDomainService = transferDomainService;
    }

    @Retryable(
            retryFor = ConcurrencyFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public TransferResponse execute(TransferRequest request) {
        Objects.requireNonNull(request, "Request null olamaz");

        if (request.senderIban().equalsIgnoreCase(request.receiverIban())) {
            throw new SameAccountTransferException(request.senderIban());
        }

        // Fetch account basic info without lock first
        AccountInfo senderInfo = accountInternalService.getAccountInfoForTransfer(request.senderIban());
        AccountInfo receiverInfo = accountInternalService.getAccountInfoForTransfer(request.receiverIban());

        validateAccountsActive(senderInfo, receiverInfo, request.senderIban(), request.receiverIban());

        Money amount = new Money(request.amount(), request.currency());

        // Validate transfer domain rules and create the Transfer aggregate root
        Transfer transfer = createAndValidateTransfer(senderInfo, receiverInfo, request.senderIban(), request.receiverIban(), amount);

        // Save initially as PENDING
        Transfer savedTransfer = saveTransferPort.save(transfer);

        // Perform balance updates and locking inside Account module
        accountInternalService.debitAndCredit(senderInfo.id(), receiverInfo.id(), amount);

        // Complete the transfer
        savedTransfer.complete();

        // Save the updated COMPLETED transfer
        Transfer completedTransfer = saveTransferPort.save(savedTransfer);

        logAuditAndPublishEvent(completedTransfer, request.senderIban(), request.receiverIban());

        return TransferResponse.from(completedTransfer, request.senderIban(), request.receiverIban());
    }

    private void validateAccountsActive(AccountInfo sender, AccountInfo receiver, String senderIban, String receiverIban) {
        if (!sender.active()) {
            throw new AccountNotActiveException(senderIban);
        }
        if (!receiver.active()) {
            throw new AccountNotActiveException(receiverIban);
        }
    }

    private Transfer createAndValidateTransfer(AccountInfo sender, AccountInfo receiver, String senderIban, String receiverIban, Money amount) {
        return transferDomainService.execute(
                sender.id(),
                senderIban,
                Money.Currency.valueOf(sender.currency()),
                receiver.id(),
                receiverIban,
                Money.Currency.valueOf(receiver.currency()),
                amount
        );
    }

    private void logAuditAndPublishEvent(Transfer savedTransfer, String senderIban, String receiverIban) {
        // Audit log
        auditService.log(
                AuditAction.TRANSFER_EXECUTED,
                String.format(
                        "Para transferi gerçekleştirildi. Transfer ID: %d, Gönderici IBAN: %s, Alıcı IBAN: %s, Tutar: %s %s",
                        savedTransfer.getId(), senderIban, receiverIban,
                        savedTransfer.getAmount().amount(), savedTransfer.getAmount().currency().name()));

        // Publish event to decouple notification sending
        eventPublisher.publishEvent(new TransferCompletedEvent(savedTransfer));
    }
}
