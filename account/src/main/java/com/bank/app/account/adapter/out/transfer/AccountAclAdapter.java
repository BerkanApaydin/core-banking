package com.bank.app.account.adapter.out.transfer;

import com.bank.app.account.application.port.in.AccountQueryUseCase;
import com.bank.app.account.application.port.in.ExecuteTransferUseCase;
import com.bank.app.account.application.port.in.ReverseTransferUseCase;
import com.bank.app.common.application.port.out.AccountAclPort;
import com.bank.app.common.domain.Money;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

@Component
public class AccountAclAdapter implements AccountAclPort {

    private final AccountQueryUseCase accountQueryUseCase;
    private final ExecuteTransferUseCase executeTransferUseCase;
    private final ReverseTransferUseCase reverseTransferUseCase;

    public AccountAclAdapter(AccountQueryUseCase accountQueryUseCase,
                             ExecuteTransferUseCase executeTransferUseCase,
                             ReverseTransferUseCase reverseTransferUseCase) {
        this.accountQueryUseCase = accountQueryUseCase;
        this.executeTransferUseCase = executeTransferUseCase;
        this.reverseTransferUseCase = reverseTransferUseCase;
    }

    @Override
    public AccountInfo getAccountInfo(Long accountId) {
        var info = accountQueryUseCase.getAccountInfo(accountId);
        return new AccountInfo(info.id(), info.userId(), info.currency(), info.status());
    }

    @Override
    public AccountInfo getAccountInfoForTransfer(String ibanValue) {
        var info = accountQueryUseCase.getAccountInfoForTransfer(ibanValue);
        return new AccountInfo(info.id(), info.userId(), info.currency(), info.status());
    }

    @Override
    public Map<Long, String> getIbansForAccounts(Collection<Long> accountIds) {
        return accountQueryUseCase.getIbansForAccounts(accountIds);
    }

    @Override
    public void debitAndCredit(Long senderId, Long receiverId, Money amount) {
        executeTransferUseCase.execute(senderId, receiverId, amount);
    }

    @Override
    public void reverseBalancesForCancellation(Long senderId, Long receiverId, Money amount) {
        reverseTransferUseCase.execute(senderId, receiverId, amount);
    }
}
