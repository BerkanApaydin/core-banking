package com.bank.app.account.application.usecase;

import com.bank.app.account.application.port.LoadAccountPort;
import com.bank.app.account.application.port.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.exception.AccountNotFoundException;
import com.bank.app.common.security.SecurityUtils;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional
public class AccountInternalService {

    private final LoadAccountPort loadAccountPort;
    private final SaveAccountPort saveAccountPort;
    private final SecurityUtils securityUtils;

    public AccountInternalService(LoadAccountPort loadAccountPort, SaveAccountPort saveAccountPort, SecurityUtils securityUtils) {
        this.loadAccountPort = loadAccountPort;
        this.saveAccountPort = saveAccountPort;
        this.securityUtils = securityUtils;
    }

    @Transactional(readOnly = true)
    public AccountInfo getAccountInfo(@NonNull Long accountId) {
        Account account = loadAccountPort.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        return new AccountInfo(
                Objects.requireNonNull(account.getId()),
                Objects.requireNonNull(account.getUserId()),
                Objects.requireNonNull(account.getBalance().currency().name()),
                account.isActive());
    }

    @Transactional(readOnly = true)
    public AccountInfo getAccountInfoForTransfer(String ibanValue) {
        Iban iban = new Iban(ibanValue);
        Account account = loadAccountPort.findByIban(iban)
                .orElseThrow(() -> new AccountNotFoundException(ibanValue));
        return new AccountInfo(
                Objects.requireNonNull(account.getId()),
                Objects.requireNonNull(account.getUserId()),
                Objects.requireNonNull(account.getBalance().currency().name()),
                account.isActive());
    }

    @Transactional(readOnly = true)
    public Map<Long, String> getIbansForAccounts(Collection<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Map.of();
        }
        return loadAccountPort.findByIds(accountIds).stream()
                .collect(Collectors.toMap(Account::getId, a -> a.getIban().value()));
    }

    public void debitAndCredit(@NonNull Long senderId, @NonNull Long receiverId, @NonNull Money amount) {
        LockedAccounts locked = loadAccountsWithLockOrdered(senderId, receiverId);
        Account sender = locked.sender();
        Account receiver = locked.receiver();

        // Authorization check on sender account
        securityUtils.checkUserAuthorization(sender.getUserId(), "Bu hesaptan transfer yapmaya yetkiniz yok.");

        sender.debit(amount);
        receiver.credit(amount);

        saveAccountPort.save(sender);
        saveAccountPort.save(receiver);
    }

    public void reverseBalancesForCancellation(@NonNull Long senderId, @NonNull Long receiverId,
            @NonNull Money amount) {
        LockedAccounts locked = loadAccountsWithLockOrdered(senderId, receiverId);
        Account sender = locked.sender();
        Account receiver = locked.receiver();

        // Authorization check on sender account (since original sender cancels)
        securityUtils.checkUserAuthorization(sender.getUserId(), "Bu transferi iptal etmeye yetkiniz yok.");

        sender.credit(amount);
        receiver.debit(amount);

        saveAccountPort.save(sender);
        saveAccountPort.save(receiver);
    }

    private LockedAccounts loadAccountsWithLockOrdered(@NonNull Long senderId, @NonNull Long receiverId) {
        Account sender;
        Account receiver;
        if (senderId.compareTo(receiverId) < 0) {
            sender = loadAccountPort.findByIdWithLock(senderId)
                    .orElseThrow(() -> new AccountNotFoundException(senderId));
            receiver = loadAccountPort.findByIdWithLock(receiverId)
                    .orElseThrow(() -> new AccountNotFoundException(receiverId));
        } else {
            receiver = loadAccountPort.findByIdWithLock(receiverId)
                    .orElseThrow(() -> new AccountNotFoundException(receiverId));
            sender = loadAccountPort.findByIdWithLock(senderId)
                    .orElseThrow(() -> new AccountNotFoundException(senderId));
        }
        return new LockedAccounts(sender, receiver);
    }

    private record LockedAccounts(Account sender, Account receiver) {}

    public record AccountInfo(@NonNull Long id, @NonNull Long userId, @NonNull String currency, boolean active) {
    }
}
