package com.bank.app.transfer.infrastructure.adapter;

import com.bank.app.account.application.port.in.AccountQueryPort;
import com.bank.app.account.application.port.in.AccountTransferOperationPort;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.application.port.out.AccountOperationPort;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

@Component
public class AccountOperationAdapter implements AccountOperationPort {

    private final AccountQueryPort accountQueryPort;
    private final AccountTransferOperationPort accountTransferOperation;

    public AccountOperationAdapter(AccountQueryPort accountQueryPort,
            AccountTransferOperationPort accountTransferOperation) {
        this.accountQueryPort = accountQueryPort;
        this.accountTransferOperation = accountTransferOperation;
    }

    @Override
    public @NonNull AccountInfo getAccountInfo(Long accountId) {
        com.bank.app.account.application.port.in.AccountInfo info = accountQueryPort.getAccountInfo(accountId);
        return new AccountInfo(info.id(), info.userId(), info.currency(), info.status() == AccountStatus.ACTIVE);
    }

    @Override
    public @NonNull AccountInfo getAccountInfoForTransfer(String ibanValue) {
        com.bank.app.account.application.port.in.AccountInfo info = accountQueryPort
                .getAccountInfoForTransfer(ibanValue);
        return new AccountInfo(info.id(), info.userId(), info.currency(), info.status() == AccountStatus.ACTIVE);
    }

    @Override
    public Map<Long, String> getIbansForAccounts(Collection<Long> accountIds) {
        return accountQueryPort.getIbansForAccounts(accountIds);
    }

    @Override
    public void debitAndCredit(Long senderId, Long receiverId, Money amount) {
        accountTransferOperation.executeTransfer(senderId, receiverId, amount);
    }

    @Override
    public void reverseBalancesForCancellation(Long senderId, Long receiverId, Money amount) {
        accountTransferOperation.reverseTransfer(senderId, receiverId, amount);
    }
}
