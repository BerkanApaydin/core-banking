package com.bank.app.account.application.usecase;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.port.in.GetAccountsByUserQuery;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.service.AccountAuthorizationService;
import com.bank.app.common.application.ReadOnlyUseCase;
import java.util.List;

@ReadOnlyUseCase
public class GetAccountsByUserQueryHandler implements GetAccountsByUserQuery {

    private final LoadAccountPort loadAccountPort;
    private final AccountAuthorizationService accountAuthorizationService;

    public GetAccountsByUserQueryHandler(LoadAccountPort loadAccountPort, AccountAuthorizationService accountAuthorizationService) {
        this.loadAccountPort = loadAccountPort;
        this.accountAuthorizationService = accountAuthorizationService;
    }

    @Override
    public List<AccountResponse> execute(int page, int size) {
        Long currentUserId = accountAuthorizationService.getCurrentUserId();
        return loadAccountPort.findByUserId(currentUserId).stream()
                .map(AccountResponse::from)
                .toList();
    }
}
