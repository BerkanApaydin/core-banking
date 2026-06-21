package com.bank.app.account.application.usecase;

import com.bank.app.account.application.port.in.AccountTransferOperationPort;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.application.exception.AccountNotFoundException;
import com.bank.app.common.domain.Money;
import com.bank.app.common.security.port.out.SecurityContextPort;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class AccountTransferOperationUseCase implements AccountTransferOperationPort {

    private final LoadAccountPort loadAccountPort;
    private final SaveAccountPort saveAccountPort;
    private final SecurityContextPort securityContextPort;

    public AccountTransferOperationUseCase(LoadAccountPort loadAccountPort,
            SaveAccountPort saveAccountPort,
            SecurityContextPort securityContextPort) {
        this.loadAccountPort = loadAccountPort;
        this.saveAccountPort = saveAccountPort;
        this.securityContextPort = securityContextPort;
    }

    @Override
    public void executeTransfer(Long senderId, Long receiverId, Money amount) {
        LockedAccounts locked = loadAccountsWithLockOrdered(senderId, receiverId);
        Account sender = locked.sender();
        Account receiver = locked.receiver();

        securityContextPort.checkUserAuthorization(sender.getUserId(), "Bu hesaptan transfer yapmaya yetkiniz yok.");

        sender.debit(amount);
        receiver.credit(amount);

        saveAccountPort.save(sender);
        saveAccountPort.save(receiver);
    }

    @Override
    public void reverseTransfer(Long senderId, Long receiverId, Money amount) {
        LockedAccounts locked = loadAccountsWithLockOrdered(senderId, receiverId);
        Account sender = locked.sender();
        Account receiver = locked.receiver();

        securityContextPort.checkUserAuthorization(sender.getUserId(), "Bu transferi iptal etmeye yetkiniz yok.");

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

    private record LockedAccounts(Account sender, Account receiver) {
    }
}
