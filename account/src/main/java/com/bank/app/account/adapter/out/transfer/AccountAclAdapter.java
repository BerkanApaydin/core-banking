package com.bank.app.account.adapter.out.transfer;

import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.exception.AccountNotFoundException;
import com.bank.app.transfer.application.port.out.AccountAclPort;
import com.bank.app.common.domain.event.DomainEvent;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.OrderedPair;
import com.bank.app.common.application.port.out.ClockProviderPort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class AccountAclAdapter implements AccountAclPort {

    private final LoadAccountPort loadAccountPort;
    private final SaveAccountPort saveAccountPort;
    private final ClockProviderPort clockProvider;

    public AccountAclAdapter(LoadAccountPort loadAccountPort,
            SaveAccountPort saveAccountPort,
            ClockProviderPort clockProvider) {
        this.loadAccountPort = loadAccountPort;
        this.saveAccountPort = saveAccountPort;
        this.clockProvider = clockProvider;
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
    public List<DomainEvent> debitAndCredit(Long senderId, Long receiverId, Money amount) {
        Objects.requireNonNull(amount, "Amount must not be null");
        OrderedPair<Account> pair = loadOrderedPair(senderId, receiverId, loadAccountPort);
        Account sender = resolveSender(pair, senderId, receiverId);
        Account receiver = resolveReceiver(pair, senderId, receiverId);
        sender.debit(amount, clockProvider.clock());
        receiver.credit(amount, clockProvider.clock());
        saveAccounts(sender, receiver, saveAccountPort);
        return collectEvents(sender, receiver);
    }

    @Override
    public List<DomainEvent> reverseBalancesForCancellation(Long senderId, Long receiverId, Money amount) {
        Objects.requireNonNull(amount, "Amount must not be null");
        OrderedPair<Account> pair = loadOrderedPair(senderId, receiverId, loadAccountPort);
        Account sender = resolveSender(pair, senderId, receiverId);
        Account receiver = resolveReceiver(pair, senderId, receiverId);
        sender.credit(amount, clockProvider.clock());
        receiver.debit(amount, clockProvider.clock());
        saveAccounts(sender, receiver, saveAccountPort);
        return collectEvents(sender, receiver);
    }

    private static List<DomainEvent> collectEvents(Account sender, Account receiver) {
        List<DomainEvent> events = new ArrayList<>();
        events.addAll(sender.getDomainEvents());
        events.addAll(receiver.getDomainEvents());
        sender.clearDomainEvents();
        receiver.clearDomainEvents();
        return events;
    }

    private static AccountInfo toAccountInfo(Account account) {
        return new AccountInfo(
                account.getId(),
                account.getUserId().value(),
                account.getBalance().currency().name(),
                account.getStatus().name());
    }

    private static OrderedPair<Account> loadOrderedPair(Long id1, Long id2, LoadAccountPort port) {
        Objects.requireNonNull(id1, "id1 must not be null");
        Objects.requireNonNull(id2, "id2 must not be null");
        return OrderedPair.from(
                id1, () -> port.findByIdWithLock(id1)
                        .orElseThrow(() -> new AccountNotFoundException(id1)),
                id2, () -> port.findByIdWithLock(id2)
                        .orElseThrow(() -> new AccountNotFoundException(id2)));
    }

    private static Account resolveSender(OrderedPair<Account> pair, Long senderId, Long receiverId) {
        return senderId < receiverId ? pair.lowerIdItem() : pair.higherIdItem();
    }

    private static Account resolveReceiver(OrderedPair<Account> pair, Long senderId, Long receiverId) {
        return senderId < receiverId ? pair.higherIdItem() : pair.lowerIdItem();
    }

    private static void saveAccounts(Account sender, Account receiver, SaveAccountPort savePort) {
        savePort.save(sender);
        savePort.save(receiver);
    }
}
