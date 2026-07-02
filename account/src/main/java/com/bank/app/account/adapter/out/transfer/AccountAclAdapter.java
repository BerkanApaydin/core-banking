package com.bank.app.account.adapter.out.transfer;

import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.exception.AccountNotFoundException;
import com.bank.app.common.application.port.out.AccountAclPort;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.application.service.DomainEventPublisher;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.OrderedPair;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class AccountAclAdapter implements AccountAclPort {

    private final LoadAccountPort loadAccountPort;
    private final SaveAccountPort saveAccountPort;
    private final EventPublisherPort eventPublisherPort;

    public AccountAclAdapter(LoadAccountPort loadAccountPort,
                             SaveAccountPort saveAccountPort,
                             EventPublisherPort eventPublisherPort) {
        this.loadAccountPort = loadAccountPort;
        this.saveAccountPort = saveAccountPort;
        this.eventPublisherPort = eventPublisherPort;
    }

    @Override
    public AccountInfo getAccountInfo(Long accountId) {
        Account account = loadAccountPort.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        return toAccountInfo(account);
    }

    @Override
    public AccountInfo getAccountInfoForTransfer(String ibanValue) {
        Account account = loadAccountPort.findByIban(new com.bank.app.common.domain.Iban(ibanValue))
                .orElseThrow(() -> new AccountNotFoundException(ibanValue));
        return toAccountInfo(account);
    }

    @Override
    public Map<Long, String> getIbansForAccounts(Collection<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Map.of();
        }
        return loadAccountPort.findByIds(accountIds).stream()
                .collect(Collectors.toMap(Account::getId, a -> a.getIban().value()));
    }

    @Override
    public void debitAndCredit(Long senderId, Long receiverId, Money amount) {
        Objects.requireNonNull(amount, "Amount must not be null");
        OrderedPair<Account> pair = loadOrderedPair(senderId, receiverId);
        Account sender = resolveSender(pair, senderId, receiverId);
        Account receiver = resolveReceiver(pair, senderId, receiverId);
        sender.debit(amount);
        receiver.credit(amount);
        saveAndPublishEvents(sender, receiver);
    }

    @Override
    public void reverseBalancesForCancellation(Long senderId, Long receiverId, Money amount) {
        Objects.requireNonNull(amount, "Amount must not be null");
        OrderedPair<Account> pair = loadOrderedPair(senderId, receiverId);
        Account sender = resolveSender(pair, senderId, receiverId);
        Account receiver = resolveReceiver(pair, senderId, receiverId);
        sender.credit(amount);
        receiver.debit(amount);
        saveAndPublishEvents(sender, receiver);
    }

    private OrderedPair<Account> loadOrderedPair(Long id1, Long id2) {
        return OrderedPair.from(
                id1, () -> loadAccountPort.findByIdWithLock(id1)
                        .orElseThrow(() -> new AccountNotFoundException(id1)),
                id2, () -> loadAccountPort.findByIdWithLock(id2)
                        .orElseThrow(() -> new AccountNotFoundException(id2)));
    }

    private static Account resolveSender(OrderedPair<Account> pair, Long senderId, Long receiverId) {
        return senderId < receiverId ? pair.lowerIdItem() : pair.higherIdItem();
    }

    private static Account resolveReceiver(OrderedPair<Account> pair, Long senderId, Long receiverId) {
        return senderId < receiverId ? pair.higherIdItem() : pair.lowerIdItem();
    }

    private void saveAndPublishEvents(Account sender, Account receiver) {
        saveAccountPort.save(sender);
        saveAccountPort.save(receiver);
        DomainEventPublisher.publishEvents(sender, eventPublisherPort);
        DomainEventPublisher.publishEvents(receiver, eventPublisherPort);
    }

    private static AccountInfo toAccountInfo(Account account) {
        return new AccountInfo(
                Objects.requireNonNull(account.getId()),
                Objects.requireNonNull(account.getUserId()).value(),
                Objects.requireNonNull(account.getBalance().currency().name()),
                account.getStatus().name());
    }
}
