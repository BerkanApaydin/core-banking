package com.bank.app.account.application.usecase;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.exception.AccountNotFoundException;
import com.bank.app.account.application.port.in.GetAccountByIbanQuery;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.service.AccountAuthorizationService;
import com.bank.app.account.domain.Account;
import com.bank.app.common.application.ReadOnlyUseCase;
import com.bank.app.common.domain.Iban;
import java.util.Objects;

@ReadOnlyUseCase
public class GetAccountByIbanQueryHandler implements GetAccountByIbanQuery {

    private final LoadAccountPort loadAccountPort;
    private final AccountAuthorizationService accountAuthorizationService;

    public GetAccountByIbanQueryHandler(LoadAccountPort loadAccountPort, AccountAuthorizationService accountAuthorizationService) {
        this.loadAccountPort = loadAccountPort;
        this.accountAuthorizationService = accountAuthorizationService;
    }

    @Override
    public AccountResponse execute(String ibanValue) {
        Objects.requireNonNull(ibanValue, "IBAN must not be null");
        Iban iban = new Iban(ibanValue);
        Account account = loadAccountPort.findByIban(iban)
            .orElseThrow(() -> new AccountNotFoundException(ibanValue));
        accountAuthorizationService.authorizeAccountOwner(account, "Bu hesaba erişim yetkiniz yok");
        return AccountResponse.from(account);
    }
}
