package com.bank.app.account.infrastructure.decorator;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.dto.CreateAccountRequest;
import com.bank.app.account.application.port.in.CreateAccountPort;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.application.usecase.CreateAccountUseCase;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.security.port.out.SecurityContextPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class CreateAccountUseCaseDecorator implements CreateAccountPort {

    private final CreateAccountUseCase delegate;

    public CreateAccountUseCaseDecorator(
            LoadAccountPort loadAccountPort,
            SaveAccountPort saveAccountPort,
            EventPublisherPort eventPublisherPort,
            SecurityContextPort securityContextPort) {
        this.delegate = new CreateAccountUseCase(
                loadAccountPort, saveAccountPort, eventPublisherPort, securityContextPort);
    }

    @Override
    public AccountResponse execute(CreateAccountRequest request) {
        return delegate.execute(request);
    }
}
