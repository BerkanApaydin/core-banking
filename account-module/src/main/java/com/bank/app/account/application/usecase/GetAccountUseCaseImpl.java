package com.bank.app.account.application.usecase;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.Iban;
import com.bank.app.account.application.exception.AccountNotFoundException;
import com.bank.app.account.application.port.in.GetAccountQuery;
import com.bank.app.common.exception.AuthorizationException;
import com.bank.app.common.security.port.out.SecurityContextPort;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class GetAccountUseCaseImpl implements GetAccountQuery {

    private final LoadAccountPort loadAccountPort;
    private final SecurityContextPort securityContextPort;

    public GetAccountUseCaseImpl(LoadAccountPort loadAccountPort, SecurityContextPort securityContextPort) {
        this.loadAccountPort = loadAccountPort;
        this.securityContextPort = securityContextPort;
    }

    public AccountResponse getById(Long id) {
        Objects.requireNonNull(id, "Account ID null olamaz");
        Account account = loadAccountPort.findById(id)
            .orElseThrow(() -> new AccountNotFoundException(id));
        checkAuthorization(account);
        return AccountResponse.from(account);
    }

    public AccountResponse getByIban(String ibanValue) {
        Objects.requireNonNull(ibanValue, "IBAN null olamaz");
        Iban iban = new Iban(ibanValue);
        Account account = loadAccountPort.findByIban(iban)
            .orElseThrow(() -> new AccountNotFoundException(ibanValue));
        checkAuthorization(account);
        return AccountResponse.from(account);
    }

    public List<AccountResponse> getAll() {
        return securityContextPort.getCurrentUserId()
            .map(userId -> loadAccountPort.findByUserId(userId).stream()
                .map(AccountResponse::from)
                .collect(Collectors.toList()))
            .orElseThrow(() -> new AuthorizationException("Bu işlem için giriş yapmalısınız."));
    }

    private void checkAuthorization(Account account) {
        securityContextPort.checkUserAuthorization(account.getUserId(), "Bu hesaba erişim yetkiniz yok.");
    }
}
