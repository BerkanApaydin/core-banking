package com.bank.app.account.application.usecase;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.port.in.GetAccountsByUserQuery;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.service.AccountAuthorizationService;
import com.bank.app.common.application.port.in.ReadOnlyUseCase;
import com.bank.app.common.application.dto.PageResponse;
import com.bank.app.account.domain.Account;
import java.util.List;

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
        List<Account> accounts = loadAccountPort.findByUserId(currentUserId, cappedPage, cappedSize);
        long total = loadAccountPort.countByUserId(currentUserId);
        var responses = accounts.stream()
                .map(AccountResponse::from)
                .toList();
        return PageResponse.of(responses, cappedPage, cappedSize, total);
    }
}
