package com.bank.app.account.application.usecase;

import com.bank.app.account.application.exception.AccountNotFoundException;
import com.bank.app.account.application.port.in.ExecuteTransferUseCase;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.common.application.DomainEventPublisher;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.application.port.out.security.SecurityContextPort;
import com.bank.app.common.application.UseCase;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.OrderedPair;
import com.bank.app.common.domain.event.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@UseCase
public class ExecuteTransferUseCaseImpl implements ExecuteTransferUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExecuteTransferUseCaseImpl.class);
    private final LoadAccountPort loadAccountPort;
    private final SaveAccountPort saveAccountPort;
    private final SecurityContextPort securityContextPort;
    private final EventPublisherPort eventPublisherPort;

    public ExecuteTransferUseCaseImpl(LoadAccountPort loadAccountPort, SaveAccountPort saveAccountPort,
            SecurityContextPort securityContextPort, EventPublisherPort eventPublisherPort) {
        this.loadAccountPort = loadAccountPort;
        this.saveAccountPort = saveAccountPort;
        this.securityContextPort = securityContextPort;
        this.eventPublisherPort = eventPublisherPort;
    }

    @Override
    @Transactional
    public void execute(Long senderId, Long receiverId, Money amount) {
        Objects.requireNonNull(senderId, "senderId null olamaz");
        Objects.requireNonNull(receiverId, "receiverId null olamaz");

        OrderedPair<Account> pair = OrderedPair.from(
                senderId, () -> loadAccountPort.findByIdWithLock(senderId)
                        .orElseThrow(() -> new AccountNotFoundException(senderId)),
                receiverId, () -> loadAccountPort.findByIdWithLock(receiverId)
                        .orElseThrow(() -> new AccountNotFoundException(receiverId)));

        boolean senderFirst = senderId < receiverId;
        Account sender = senderFirst ? pair.lowerIdItem() : pair.higherIdItem();
        Account receiver = senderFirst ? pair.higherIdItem() : pair.lowerIdItem();

        securityContextPort.checkUserAuthorization(sender.getUserId().value(),
                "Bu hesaptan transfer yapmaya yetkiniz yok.");
        sender.debit(amount);
        receiver.credit(amount);
        saveAccountPort.save(sender);
        saveAccountPort.save(receiver);

        DomainEventPublisher.publishEvents(sender, eventPublisherPort);
        DomainEventPublisher.publishEvents(receiver, eventPublisherPort);
        eventPublisherPort.publish(new AuditEvent("TRANSFER_EXECUTED",
            String.format("Para transferi gerçekleştirildi. Gönderen: %d, Alan: %d, Tutar: %s %s",
                senderId, receiverId, amount.amount(), amount.currency()),
            java.time.LocalDateTime.now()));

        log.info("Transfer executed: senderId={}, receiverId={}, amount={}",
            senderId, receiverId, amount);
    }
}
