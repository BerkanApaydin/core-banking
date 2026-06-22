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
import com.bank.app.account.infrastructure.decorator.AccountUseCaseTransactionDecorator;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.security.port.out.SecurityContextPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AccountDomainConfig {

    @Bean
    @Qualifier("rawCreateAccountUseCase")
    public CreateAccountUseCase rawCreateAccountUseCase(LoadAccountPort loadAccountPort, SaveAccountPort saveAccountPort,
                                                         EventPublisherPort eventPublisherPort, SecurityContextPort securityContextPort) {
        return new CreateAccountUseCaseImpl(loadAccountPort, saveAccountPort, eventPublisherPort, securityContextPort);
    }

    @Bean
    @Qualifier("rawAccountTransferOperationPort")
    public AccountTransferOperationPort rawAccountTransferOperationPort(LoadAccountPort loadAccountPort,
                                                                         SaveAccountPort saveAccountPort,
                                                                         SecurityContextPort securityContextPort) {
        return new AccountTransferOperationUseCase(loadAccountPort, saveAccountPort, securityContextPort);
    }

    @Bean
    @Primary
    public AccountUseCaseTransactionDecorator accountUseCase(
            @Qualifier("rawCreateAccountUseCase") CreateAccountUseCase createAccountUseCase,
            @Qualifier("rawAccountTransferOperationPort") AccountTransferOperationPort accountTransferOperationPort) {
        return new AccountUseCaseTransactionDecorator(createAccountUseCase, accountTransferOperationPort);
    }

    @Bean
    public GetAccountQuery getAccountQuery(LoadAccountPort loadAccountPort, SecurityContextPort securityContextPort) {
        return new GetAccountUseCaseImpl(loadAccountPort, securityContextPort);
    }

    @Bean
    public AccountQueryPort accountQueryPort(LoadAccountPort loadAccountPort) {
        return new AccountQueryService(loadAccountPort);
    }
}
