package com.bank.app.account.infrastructure.decorator;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.port.in.GetAccountPort;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.usecase.GetAccountUseCase;
import com.bank.app.common.security.port.out.SecurityContextPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional(readOnly = true)
public class GetAccountUseCaseDecorator implements GetAccountPort {

    private final GetAccountUseCase delegate;

    public GetAccountUseCaseDecorator(
            LoadAccountPort loadAccountPort,
            SecurityContextPort securityContextPort) {
        this.delegate = new GetAccountUseCase(loadAccountPort, securityContextPort);
    }

    @Override
    public AccountResponse getById(Long id) {
        return delegate.getById(id);
    }

    @Override
    public AccountResponse getByIban(String iban) {
        return delegate.getByIban(iban);
    }

    @Override
    public List<AccountResponse> getAll() {
        return delegate.getAll();
    }
}
