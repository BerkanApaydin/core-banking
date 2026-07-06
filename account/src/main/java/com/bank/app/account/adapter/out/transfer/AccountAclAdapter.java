package com.bank.app.account.adapter.out.transfer;

import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.exception.AccountNotFoundException;
import com.bank.app.account.application.usecase.TransferAccountHelper;
import com.bank.app.common.application.port.out.AccountAclPort;
import com.bank.app.common.domain.event.DomainEvent;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.OrderedPair;
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

    public AccountAclAdapter(LoadAccountPort loadAccountPort,
                             SaveAccountPort saveAccountPort) {
        this.loadAccountPort = loadAccountPort;
        this.saveAccountPort = saveAccountPort;
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
        OrderedPair<Account> pair = TransferAccountHelper.loadOrderedPair(senderId, receiverId, loadAccountPort);
        Account sender = TransferAccountHelper.resolveSender(pair, senderId, receiverId);
        Account receiver = TransferAccountHelper.resolveReceiver(pair, senderId, receiverId);
        sender.debit(amount);
        receiver.credit(amount);
        TransferAccountHelper.saveAccounts(sender, receiver, saveAccountPort);
        return collectEvents(sender, receiver);
    }

    @Override
    public List<DomainEvent> reverseBalancesForCancellation(Long senderId, Long receiverId, Money amount) {
        Objects.requireNonNull(amount, "Amount must not be null");
        OrderedPair<Account> pair = TransferAccountHelper.loadOrderedPair(senderId, receiverId, loadAccountPort);
        Account sender = TransferAccountHelper.resolveSender(pair, senderId, receiverId);
        Account receiver = TransferAccountHelper.resolveReceiver(pair, senderId, receiverId);
        sender.credit(amount);
        receiver.debit(amount);
        TransferAccountHelper.saveAccounts(sender, receiver, saveAccountPort);
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
                account.getStatus().name()
        );
    }
}
