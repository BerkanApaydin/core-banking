package com.bank.app.account.application.usecase;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.domain.exception.AccountNotFoundException;
import com.bank.app.account.application.port.in.GetAccountByIdQuery;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.service.AccountAuthorizationService;
import com.bank.app.account.domain.Account;
import com.bank.app.common.application.port.in.ReadOnlyUseCase;
import java.util.Objects;

@ReadOnlyUseCase
public class GetAccountByIdQueryHandler implements GetAccountByIdQuery {

    private final LoadAccountPort loadAccountPort;
    private final AccountAuthorizationService accountAuthorizationService;

    public GetAccountByIdQueryHandler(LoadAccountPort loadAccountPort, AccountAuthorizationService accountAuthorizationService) {
        this.loadAccountPort = loadAccountPort;
        this.accountAuthorizationService = accountAuthorizationService;
    }

    @Override
    public AccountResponse execute(Long id) {
        Objects.requireNonNull(id, "Account ID must not be null");
        Account account = loadAccountPort.findById(id)
            .orElseThrow(() -> new AccountNotFoundException(id));
        accountAuthorizationService.authorizeAccountOwner(account, "You do not have access to this account");
        return AccountResponse.from(account);
    }
}
