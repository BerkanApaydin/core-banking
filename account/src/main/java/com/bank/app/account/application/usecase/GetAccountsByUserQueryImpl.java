package com.bank.app.account.application.usecase;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.port.in.GetAccountsByUserQuery;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.service.AccountAuthorizationService;
import com.bank.app.common.application.port.in.ReadOnlyUseCase;
import com.bank.app.common.application.dto.PageResponse;

@ReadOnlyUseCase
public class GetAccountsByUserQueryImpl implements GetAccountsByUserQuery {

    private static final int MAX_PAGE_SIZE = 100;

    private final LoadAccountPort loadAccountPort;
    private final AccountAuthorizationService accountAuthorizationService;

    public GetAccountsByUserQueryImpl(LoadAccountPort loadAccountPort, AccountAuthorizationService accountAuthorizationService) {
        this.loadAccountPort = loadAccountPort;
        this.accountAuthorizationService = accountAuthorizationService;
    }

    @Override
    public PageResponse<AccountResponse> execute(int page, int size) {
        Long currentUserId = accountAuthorizationService.getCurrentUserId();
        int cappedPage = Math.max(page, 0);
        int cappedSize = Math.max(Math.min(size, MAX_PAGE_SIZE), 1);
        var accounts = loadAccountPort.findPageByUserId(currentUserId, cappedPage, cappedSize);
        var responses = accounts.content().stream()
                .map(AccountResponse::from)
                .toList();
        return PageResponse.of(responses, cappedPage, cappedSize, accounts.total());
    }
}
