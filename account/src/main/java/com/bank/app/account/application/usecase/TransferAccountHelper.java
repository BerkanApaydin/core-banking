package com.bank.app.account.application.usecase;

import com.bank.app.account.domain.exception.AccountNotFoundException;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.common.domain.OrderedPair;

import java.util.Objects;

public class TransferAccountHelper {

    public static OrderedPair<Account> loadOrderedPair(Long id1, Long id2, LoadAccountPort port) {
        Objects.requireNonNull(id1, "id1 must not be null");
        Objects.requireNonNull(id2, "id2 must not be null");
        return OrderedPair.from(
                id1, () -> port.findByIdWithLock(id1)
                        .orElseThrow(() -> new AccountNotFoundException(id1)),
                id2, () -> port.findByIdWithLock(id2)
                        .orElseThrow(() -> new AccountNotFoundException(id2)));
    }

    public static Account resolveSender(OrderedPair<Account> pair, Long senderId, Long receiverId) {
        return senderId < receiverId ? pair.lowerIdItem() : pair.higherIdItem();
    }

    public static Account resolveReceiver(OrderedPair<Account> pair, Long senderId, Long receiverId) {
        return senderId < receiverId ? pair.higherIdItem() : pair.lowerIdItem();
    }

    public static void saveAccounts(Account sender, Account receiver, SaveAccountPort savePort) {
        savePort.save(sender);
        savePort.save(receiver);
    }
}
