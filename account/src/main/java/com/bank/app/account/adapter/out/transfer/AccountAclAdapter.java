package com.bank.app.account.adapter.out.transfer;

import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.exception.AccountNotFoundException;
import com.bank.app.account.application.usecase.TransferAccountHelper;
import com.bank.app.common.application.port.out.AccountAclPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
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
    private final DomainEventPublisherService domainEventPublisherService;

    public AccountAclAdapter(LoadAccountPort loadAccountPort,
                             SaveAccountPort saveAccountPort,
                             DomainEventPublisherService domainEventPublisherService) {
        this.loadAccountPort = loadAccountPort;
        this.saveAccountPort = saveAccountPort;
        this.domainEventPublisherService = domainEventPublisherService;
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
        OrderedPair<Account> pair = TransferAccountHelper.loadOrderedPair(senderId, receiverId, loadAccountPort);
        Account sender = TransferAccountHelper.resolveSender(pair, senderId, receiverId);
        Account receiver = TransferAccountHelper.resolveReceiver(pair, senderId, receiverId);
        sender.debit(amount);
        receiver.credit(amount);
        TransferAccountHelper.saveAndPublishEvents(sender, receiver, saveAccountPort, domainEventPublisherService);
    }

    @Override
    public void reverseBalancesForCancellation(Long senderId, Long receiverId, Money amount) {
        Objects.requireNonNull(amount, "Amount must not be null");
        OrderedPair<Account> pair = TransferAccountHelper.loadOrderedPair(senderId, receiverId, loadAccountPort);
        Account sender = TransferAccountHelper.resolveSender(pair, senderId, receiverId);
        Account receiver = TransferAccountHelper.resolveReceiver(pair, senderId, receiverId);
        sender.credit(amount);
        receiver.debit(amount);
        TransferAccountHelper.saveAndPublishEvents(sender, receiver, saveAccountPort, domainEventPublisherService);
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
