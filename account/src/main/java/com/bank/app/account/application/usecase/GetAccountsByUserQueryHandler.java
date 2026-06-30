package com.bank.app.account.application.usecase;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.port.in.GetAccountsByUserQuery;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.service.AccountAuthorizationService;
import com.bank.app.common.application.ReadOnlyUseCase;
import com.bank.app.common.application.dto.PageResponse;
import org.springframework.data.domain.PageRequest;

@ReadOnlyUseCase
public class GetAccountsByUserQueryHandler implements GetAccountsByUserQuery {

    private final LoadAccountPort loadAccountPort;
    private final AccountAuthorizationService accountAuthorizationService;

    public GetAccountsByUserQueryHandler(LoadAccountPort loadAccountPort, AccountAuthorizationService accountAuthorizationService) {
        this.loadAccountPort = loadAccountPort;
        this.accountAuthorizationService = accountAuthorizationService;
    }

    @Override
    public PageResponse<AccountResponse> execute(int page, int size) {
        Long currentUserId = accountAuthorizationService.getCurrentUserId();
        var accountPage = loadAccountPort.findByUserId(currentUserId, PageRequest.of(page, size));
        var responses = accountPage.getContent().stream()
                .map(AccountResponse::from)
                .toList();
        return PageResponse.of(responses, accountPage.getNumber(), accountPage.getSize(), accountPage.getTotalElements());
    }
}
