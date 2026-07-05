package com.bank.app.account.adapter.in.config;

import com.bank.app.account.application.port.in.CreateAccountUseCase;
import com.bank.app.account.application.port.in.GetAccountByIdQuery;
import com.bank.app.account.application.port.in.GetAccountByIbanQuery;
import com.bank.app.account.application.port.in.GetAccountsByUserQuery;
import com.bank.app.account.application.port.in.AccountQueryUseCase;
import com.bank.app.account.application.service.AccountAuthorizationService;
import com.bank.app.account.application.usecase.CreateAccountUseCaseImpl;
import com.bank.app.account.application.usecase.GetAccountByIdQueryHandler;
import com.bank.app.account.application.usecase.GetAccountByIbanQueryHandler;
import com.bank.app.account.application.usecase.GetAccountsByUserQueryHandler;
import com.bank.app.account.application.usecase.AccountQueryUseCaseImpl;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.application.service.UserContextService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AccountBeanConfig {

    @Bean
    public CreateAccountUseCase createAccountUseCase(LoadAccountPort loadAccountPort, SaveAccountPort saveAccountPort,
            DomainEventPublisherService domainEventPublisherService, AuditEventPort auditEventPort, AccountAuthorizationService accountAuthorizationService) {
        return new CreateAccountUseCaseImpl(loadAccountPort, saveAccountPort, domainEventPublisherService, auditEventPort, accountAuthorizationService);
    }

    @Bean
    public AccountAuthorizationService accountAuthorizationService(UserContextService userContextService) {
        return new AccountAuthorizationService(userContextService);
    }


    @Bean
    public GetAccountByIdQuery getAccountByIdQuery(LoadAccountPort loadAccountPort,
            AccountAuthorizationService accountAuthorizationService) {
        return new GetAccountByIdQueryHandler(loadAccountPort, accountAuthorizationService);
    }

    @Bean
    public GetAccountByIbanQuery getAccountByIbanQuery(LoadAccountPort loadAccountPort,
            AccountAuthorizationService accountAuthorizationService) {
        return new GetAccountByIbanQueryHandler(loadAccountPort, accountAuthorizationService);
    }

    @Bean
    public GetAccountsByUserQuery getAccountsByUserQuery(LoadAccountPort loadAccountPort,
            AccountAuthorizationService accountAuthorizationService) {
        return new GetAccountsByUserQueryHandler(loadAccountPort, accountAuthorizationService);
    }

    @Bean
    public AccountQueryUseCase accountQueryUseCase(LoadAccountPort loadAccountPort) {
        return new AccountQueryUseCaseImpl(loadAccountPort);
    }
}
