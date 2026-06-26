package com.bank.app.account.application.usecase;

import com.bank.app.account.application.exception.AccountNotFoundException;
import com.bank.app.account.application.port.in.ReverseTransferUseCase;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.common.application.UseCase;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.application.port.out.security.SecurityContextPort;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.OrderedPair;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.common.domain.event.DomainEvent;
import com.bank.app.common.domain.event.DomainEventProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Objects;

@UseCase
public class ReverseTransferUseCaseImpl implements ReverseTransferUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReverseTransferUseCaseImpl.class);

    private final LoadAccountPort loadAccountPort;
    private final SaveAccountPort saveAccountPort;
    private final SecurityContextPort securityContextPort;
    private final EventPublisherPort eventPublisherPort;

    public ReverseTransferUseCaseImpl(LoadAccountPort loadAccountPort, SaveAccountPort saveAccountPort,
                                      SecurityContextPort securityContextPort, EventPublisherPort eventPublisherPort) {
        this.loadAccountPort = loadAccountPort;
        this.saveAccountPort = saveAccountPort;
        this.securityContextPort = securityContextPort;
        this.eventPublisherPort = eventPublisherPort;
    }

    @Override
    public void execute(Long senderId, Long receiverId, Money amount) {
        Objects.requireNonNull(senderId, "Gönderici ID null olamaz");
        Objects.requireNonNull(receiverId, "Alıcı ID null olamaz");
        Objects.requireNonNull(amount, "Tutar null olamaz");

        OrderedPair<Account> pair = OrderedPair.from(
                senderId, () -> loadAccountPort.findByIdWithLock(senderId)
                        .orElseThrow(() -> new AccountNotFoundException(senderId)),
                receiverId, () -> loadAccountPort.findByIdWithLock(receiverId)
                        .orElseThrow(() -> new AccountNotFoundException(receiverId)));

        boolean senderFirst = senderId < receiverId;
        Account sender = senderFirst ? pair.lowerIdItem() : pair.higherIdItem();
        Account receiver = senderFirst ? pair.higherIdItem() : pair.lowerIdItem();

        securityContextPort.checkUserAuthorization(sender.getUserId().value(), "Bu işlem için yetkiniz yok.");

        sender.credit(amount);
        receiver.debit(amount);

        saveAccountPort.save(sender);
        saveAccountPort.save(receiver);

        publishEvents(sender);
        publishEvents(receiver);
        eventPublisherPort.publish(new AuditEvent("TRANSFER_CANCELLED",
            String.format("Transfer iptal edildi (reverse). Gönderen: %d, Alan: %d, Tutar: %s %s",
                senderId, receiverId, amount.amount(), amount.currency()),
            java.time.LocalDateTime.now()));

        log.info("Transfer reversed: senderId={}, receiverId={}, amount={}",
            senderId, receiverId, amount);
    }

    private void publishEvents(DomainEventProvider provider) {
        List<DomainEvent> events = List.copyOf(provider.getDomainEvents());
        provider.clearDomainEvents();
        for (DomainEvent event : events) {
            eventPublisherPort.publish(Objects.requireNonNull(event, "Domain event null olamaz"));
        }
    }
}
