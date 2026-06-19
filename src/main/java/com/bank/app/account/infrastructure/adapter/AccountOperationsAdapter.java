package com.bank.app.account.infrastructure.adapter;

import com.bank.app.account.application.usecase.AccountInternalService;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.application.port.AccountOperationsPort;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

@Component
public class AccountOperationsAdapter implements AccountOperationsPort {

    private final AccountInternalService accountInternalService;

    public AccountOperationsAdapter(AccountInternalService accountInternalService) {
        this.accountInternalService = accountInternalService;
    }

    @Override
    public @NonNull AccountInfo getAccountInfo(@NonNull Long accountId) {
        AccountInternalService.AccountInfo info = accountInternalService.getAccountInfo(accountId);
        return new AccountInfo(info.id(), info.userId(), info.currency(), info.active());
    }

    @Override
    public @NonNull AccountInfo getAccountInfoForTransfer(String ibanValue) {
        AccountInternalService.AccountInfo info = accountInternalService.getAccountInfoForTransfer(ibanValue);
        return new AccountInfo(info.id(), info.userId(), info.currency(), info.active());
    }

    @Override
    public Map<Long, String> getIbansForAccounts(Collection<Long> accountIds) {
        return accountInternalService.getIbansForAccounts(accountIds);
    }

    @Override
    public void debitAndCredit(@NonNull Long senderId, @NonNull Long receiverId, @NonNull Money amount) {
        accountInternalService.debitAndCredit(senderId, receiverId, amount);
    }

    @Override
    public void reverseBalancesForCancellation(@NonNull Long senderId, @NonNull Long receiverId,
            @NonNull Money amount) {
        accountInternalService.reverseBalancesForCancellation(senderId, receiverId, amount);
    }

}
