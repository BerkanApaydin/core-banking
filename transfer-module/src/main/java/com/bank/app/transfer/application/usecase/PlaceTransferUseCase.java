package com.bank.app.transfer.application.usecase;

import com.bank.app.audit.application.port.in.AuditLoggerPort;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.out.AccountOperationPort;
import com.bank.app.transfer.application.port.out.AccountOperationPort.AccountInfo;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferCompletedEvent;
import com.bank.app.transfer.application.port.in.PlaceTransferPort;
import com.bank.app.transfer.application.port.out.DomainEventPublisherPort;
import com.bank.app.transfer.domain.TransferDomainService;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Transactional
public class PlaceTransferUseCase implements PlaceTransferPort {

    private static final Logger log = LoggerFactory.getLogger(PlaceTransferUseCase.class);

    private final AccountOperationPort accountOperationPort;
    private final SaveTransferPort saveTransferPort;
    private final AuditLoggerPort auditLoggerPort;
    private final DomainEventPublisherPort eventPublisherPort;
    private final TransferDomainService transferDomainService;

    public PlaceTransferUseCase(AccountOperationPort accountOperationPort,
            SaveTransferPort saveTransferPort,
            AuditLoggerPort auditLoggerPort,
            DomainEventPublisherPort eventPublisherPort,
            TransferDomainService transferDomainService) {
        this.accountOperationPort = accountOperationPort;
        this.saveTransferPort = saveTransferPort;
        this.auditLoggerPort = auditLoggerPort;
        this.eventPublisherPort = eventPublisherPort;
        this.transferDomainService = transferDomainService;
    }

    @Retryable(
            retryFor = ConcurrencyFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2)
    )
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

        logAuditAndPublishEvent(completedTransfer, senderIban, receiverIban);

        return TransferResponse.from(completedTransfer, senderIban, receiverIban);
    }

    private static String normalizeIban(String iban) {
        return iban.replaceAll("\\s", "").toUpperCase();
    }

    private Transfer createAndValidateTransfer(AccountInfo sender, AccountInfo receiver, String senderIban,
            String receiverIban, Money amount) {
        return transferDomainService.execute(
                sender.id(),
                senderIban,
                Money.Currency.valueOf(sender.currency()),
                receiver.id(),
                receiverIban,
                Money.Currency.valueOf(receiver.currency()),
                amount);
    }

    private void logAuditAndPublishEvent(Transfer savedTransfer, String senderIban, String receiverIban) {
        try {
            auditLoggerPort.log(
                    AuditAction.TRANSFER_EXECUTED,
                    String.format(
                            "Para transferi gerçekleştirildi. Transfer ID: %d, Gönderici IBAN: %s, Alıcı IBAN: %s, Tutar: %s %s",
                            savedTransfer.getId(), senderIban, receiverIban,
                            savedTransfer.getAmount().amount(), savedTransfer.getAmount().currency().name()));
        } catch (RuntimeException e) {
            log.warn("Audit log kaydedilemedi: {}", e.getMessage(), e);
        }

        eventPublisherPort.publish(new TransferCompletedEvent(savedTransfer));
    }
}
