package com.bank.app.account.infrastructure.decorator;

import com.bank.app.account.application.port.in.AccountInfo;
import com.bank.app.account.application.port.in.AccountQueryPort;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.usecase.AccountQueryService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;

@Component
@Transactional(readOnly = true)
public class AccountQueryServiceDecorator implements AccountQueryPort {

    private final AccountQueryPort delegate;

    public AccountQueryServiceDecorator(LoadAccountPort loadAccountPort) {
        this.delegate = new AccountQueryService(loadAccountPort);
    }

    @Override
    public AccountInfo getAccountInfo(Long accountId) {
        return delegate.getAccountInfo(accountId);
    }

    @Override
    public AccountInfo getAccountInfoForTransfer(String ibanValue) {
        return delegate.getAccountInfoForTransfer(ibanValue);
    }

    @Override
    public Map<Long, String> getIbansForAccounts(Collection<Long> accountIds) {
        return delegate.getIbansForAccounts(accountIds);
    }
}
