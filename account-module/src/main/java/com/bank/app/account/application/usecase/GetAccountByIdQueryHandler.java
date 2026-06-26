package com.bank.app.account.application.usecase;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.exception.AccountNotFoundException;
import com.bank.app.account.application.port.in.GetAccountByIdQuery;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.common.application.port.out.security.SecurityContextPort;
import com.bank.app.common.application.ReadOnlyUseCase;
import java.util.Objects;

@ReadOnlyUseCase
public class GetAccountByIdQueryHandler implements GetAccountByIdQuery {

    private final LoadAccountPort loadAccountPort;
    private final SecurityContextPort securityContextPort;

    public GetAccountByIdQueryHandler(LoadAccountPort loadAccountPort, SecurityContextPort securityContextPort) {
        this.loadAccountPort = loadAccountPort;
        this.securityContextPort = securityContextPort;
    }

    @Override
    public AccountResponse execute(Long id) {
        Objects.requireNonNull(id, "Account ID null olamaz");
        Account account = loadAccountPort.findById(id)
            .orElseThrow(() -> new AccountNotFoundException(id));
        securityContextPort.checkUserAuthorization(account.getUserId().value(), "Bu hesaba erişim yetkiniz yok.");
        return AccountResponse.from(account);
    }
}
