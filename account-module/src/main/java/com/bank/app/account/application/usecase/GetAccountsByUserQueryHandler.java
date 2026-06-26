package com.bank.app.account.application.usecase;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.port.in.GetAccountsByUserQuery;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.common.application.ReadOnlyUseCase;
import com.bank.app.common.application.port.out.security.SecurityContextPort;
import com.bank.app.common.domain.exception.AuthorizationException;
import java.util.List;

@ReadOnlyUseCase
public class GetAccountsByUserQueryHandler implements GetAccountsByUserQuery {

    private final LoadAccountPort loadAccountPort;
    private final SecurityContextPort securityContextPort;

    public GetAccountsByUserQueryHandler(LoadAccountPort loadAccountPort, SecurityContextPort securityContextPort) {
        this.loadAccountPort = loadAccountPort;
        this.securityContextPort = securityContextPort;
    }

    @Override
    public List<AccountResponse> execute(int page, int size) {
        Long currentUserId = securityContextPort.getCurrentUserId()
                .orElseThrow(() -> new AuthorizationException("Bu işlem için giriş yapmalısınız."));
        return loadAccountPort.findByUserId(currentUserId).stream()
                .map(AccountResponse::from)
                .toList();
    }
}
