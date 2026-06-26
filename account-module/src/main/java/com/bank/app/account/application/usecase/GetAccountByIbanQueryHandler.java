package com.bank.app.account.application.usecase;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.exception.AccountNotFoundException;
import com.bank.app.account.application.port.in.GetAccountByIbanQuery;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.common.application.ReadOnlyUseCase;
import com.bank.app.common.application.port.out.security.SecurityContextPort;
import com.bank.app.common.domain.Iban;
import java.util.Objects;

@ReadOnlyUseCase
public class GetAccountByIbanQueryHandler implements GetAccountByIbanQuery {

    private final LoadAccountPort loadAccountPort;
    private final SecurityContextPort securityContextPort;

    public GetAccountByIbanQueryHandler(LoadAccountPort loadAccountPort, SecurityContextPort securityContextPort) {
        this.loadAccountPort = loadAccountPort;
        this.securityContextPort = securityContextPort;
    }

    @Override
    public AccountResponse execute(String ibanValue) {
        Objects.requireNonNull(ibanValue, "IBAN null olamaz");
        Iban iban = new Iban(ibanValue);
        Account account = loadAccountPort.findByIban(iban)
            .orElseThrow(() -> new AccountNotFoundException(ibanValue));
        securityContextPort.checkUserAuthorization(account.getUserId().value(), "Bu hesaba erişim yetkiniz yok.");
        return AccountResponse.from(account);
    }
}
