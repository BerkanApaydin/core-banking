package com.bank.app.account.infrastructure.config;

import com.bank.app.account.application.port.in.CreateAccountUseCase;
import com.bank.app.account.application.port.in.GetAccountQuery;
import com.bank.app.account.application.port.in.AccountTransferOperationPort;
import com.bank.app.account.application.port.in.AccountQueryPort;
import com.bank.app.account.application.usecase.CreateAccountUseCaseImpl;
import com.bank.app.account.application.usecase.GetAccountUseCaseImpl;
import com.bank.app.account.application.usecase.AccountTransferOperationUseCase;
import com.bank.app.account.application.usecase.AccountQueryService;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.security.port.out.SecurityContextPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

@Configuration
public class AccountDomainConfig {

    @Bean
    @Transactional
    public CreateAccountUseCase createAccountUseCase(LoadAccountPort loadAccountPort, SaveAccountPort saveAccountPort, EventPublisherPort eventPublisherPort, SecurityContextPort securityContextPort) {
        return new CreateAccountUseCaseImpl(loadAccountPort, saveAccountPort, eventPublisherPort, securityContextPort);
    }

    @Bean
    public GetAccountQuery getAccountQuery(LoadAccountPort loadAccountPort, SecurityContextPort securityContextPort) {
        return new GetAccountUseCaseImpl(loadAccountPort, securityContextPort);
    }

    @Bean
    @Transactional
    public AccountTransferOperationPort accountTransferOperationPort(LoadAccountPort loadAccountPort, SaveAccountPort saveAccountPort, SecurityContextPort securityContextPort) {
        return new AccountTransferOperationUseCase(loadAccountPort, saveAccountPort, securityContextPort);
    }

    @Bean
    public AccountQueryPort accountQueryPort(LoadAccountPort loadAccountPort) {
        return new AccountQueryService(loadAccountPort);
    }
}
