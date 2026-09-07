package com.bank.app.account.application.usecase;

import com.bank.app.account.application.port.in.AdjustAccountBalancesUseCase;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.exception.AccountNotFoundException;
import com.bank.app.accountapi.AccountAdjustmentResult;
import com.bank.app.common.application.port.in.TransactionalUseCase;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.OrderedPair;
import com.bank.app.common.domain.event.DomainEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@TransactionalUseCase
public class AdjustAccountBalancesUseCaseImpl implements AdjustAccountBalancesUseCase {

    private final LoadAccountPort loadAccountPort;
    private final SaveAccountPort saveAccountPort;
    private final ClockProviderPort clockProvider;
    private final DomainEventPublisherService domainEventPublisherService;

    public AdjustAccountBalancesUseCaseImpl(LoadAccountPort loadAccountPort,
            SaveAccountPort saveAccountPort,
            ClockProviderPort clockProvider,
            DomainEventPublisherService domainEventPublisherService) {
        this.loadAccountPort = loadAccountPort;
        this.saveAccountPort = saveAccountPort;
        this.clockProvider = clockProvider;
        this.domainEventPublisherService = domainEventPublisherService;
    }

    @Override
    public AccountAdjustmentResult debitAndCredit(Long senderId, Long receiverId, Money amount) {
        Objects.requireNonNull(amount, "Amount must not be null");
        requireDistinctAccounts(senderId, receiverId);
        OrderedPair<Account> pair = loadOrderedPair(senderId, receiverId);
        Account sender = resolveSender(pair, senderId, receiverId);
        Account receiver = resolveReceiver(pair, senderId, receiverId);
        sender.debit(amount, clockProvider.clock());
        receiver.credit(amount, clockProvider.clock());
        saveAccounts(sender, receiver);
        publishCollectedEvents(sender, receiver);
        return new AccountAdjustmentResult(senderId, receiverId, sender.getBalance(), receiver.getBalance());
    }

    @Override
    public AccountAdjustmentResult reverseForCancellation(Long senderId, Long receiverId, Money amount) {
        Objects.requireNonNull(amount, "Amount must not be null");
        requireDistinctAccounts(senderId, receiverId);
        OrderedPair<Account> pair = loadOrderedPair(senderId, receiverId);
        Account sender = resolveSender(pair, senderId, receiverId);
        Account receiver = resolveReceiver(pair, senderId, receiverId);
        sender.credit(amount, clockProvider.clock());
        receiver.debit(amount, clockProvider.clock());
        saveAccounts(sender, receiver);
        publishCollectedEvents(sender, receiver);
        return new AccountAdjustmentResult(senderId, receiverId, sender.getBalance(), receiver.getBalance());
    }

    private void publishCollectedEvents(Account sender, Account receiver) {
        List<DomainEvent> events = new ArrayList<>();
        events.addAll(sender.getDomainEvents());
        events.addAll(receiver.getDomainEvents());
        sender.clearDomainEvents();
        receiver.clearDomainEvents();
        events.forEach(domainEventPublisherService::publish);
    }

    private static void requireDistinctAccounts(Long senderId, Long receiverId) {
        if (Objects.equals(senderId, receiverId)) {
            throw new IllegalArgumentException("Sender and receiver accounts must be different");
        }
    }

    private OrderedPair<Account> loadOrderedPair(Long id1, Long id2) {
        Objects.requireNonNull(id1, "id1 must not be null");
        Objects.requireNonNull(id2, "id2 must not be null");
        // Lock accounts in stable ID order so concurrent transfers between the same pair
        // always acquire pessimistic locks in the same sequence (deadlock prevention).
        return OrderedPair.from(
                id1, () -> loadAccountPort.findByIdForUpdate(id1)
                        .orElseThrow(() -> new AccountNotFoundException(id1)),
                id2, () -> loadAccountPort.findByIdForUpdate(id2)
                        .orElseThrow(() -> new AccountNotFoundException(id2)));
    }

    private static Account resolveSender(OrderedPair<Account> pair, Long senderId, Long receiverId) {
        return senderId < receiverId ? pair.lowerIdItem() : pair.higherIdItem();
    }

    private static Account resolveReceiver(OrderedPair<Account> pair, Long senderId, Long receiverId) {
        return senderId < receiverId ? pair.higherIdItem() : pair.lowerIdItem();
    }

    private void saveAccounts(Account sender, Account receiver) {
        saveAccountPort.save(sender);
        saveAccountPort.save(receiver);
    }
}
