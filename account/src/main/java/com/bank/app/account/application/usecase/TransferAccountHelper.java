package com.bank.app.account.application.usecase;

import com.bank.app.account.domain.exception.AccountNotFoundException;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.common.application.service.DomainEventPublisher;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.domain.OrderedPair;

import java.util.Objects;

class TransferAccountHelper {

    static OrderedPair<Account> loadOrderedPair(Long id1, Long id2, LoadAccountPort port) {
        Objects.requireNonNull(id1, "id1 must not be null");
        Objects.requireNonNull(id2, "id2 must not be null");
        return OrderedPair.from(
                id1, () -> port.findByIdWithLock(id1)
                        .orElseThrow(() -> new AccountNotFoundException(id1)),
                id2, () -> port.findByIdWithLock(id2)
                        .orElseThrow(() -> new AccountNotFoundException(id2)));
    }

    static Account resolveSender(OrderedPair<Account> pair, Long senderId, Long receiverId) {
        return senderId < receiverId ? pair.lowerIdItem() : pair.higherIdItem();
    }

    static Account resolveReceiver(OrderedPair<Account> pair, Long senderId, Long receiverId) {
        return senderId < receiverId ? pair.higherIdItem() : pair.lowerIdItem();
    }

    static void saveAndPublishEvents(Account sender, Account receiver,
                                     SaveAccountPort savePort, EventPublisherPort eventPort) {
        savePort.save(sender);
        savePort.save(receiver);
        DomainEventPublisher.publishEvents(sender, eventPort);
        DomainEventPublisher.publishEvents(receiver, eventPort);
    }
}
