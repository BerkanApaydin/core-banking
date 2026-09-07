package com.bank.app.account.config;

import com.bank.app.account.application.port.in.CreateAccountUseCase;
import com.bank.app.account.application.port.in.AdjustAccountBalancesUseCase;
import com.bank.app.account.application.port.in.GetAccountByIdQuery;
import com.bank.app.account.application.port.in.GetAccountByIbanQuery;
import com.bank.app.account.application.port.in.GetAccountsByUserQuery;
import com.bank.app.account.application.port.in.AccountQueryUseCase;
import com.bank.app.account.application.service.AccountAuthorizationService;
import com.bank.app.account.application.usecase.CreateAccountUseCaseImpl;
import com.bank.app.account.application.usecase.AdjustAccountBalancesUseCaseImpl;
import com.bank.app.account.application.usecase.GetAccountByIdQueryImpl;
import com.bank.app.account.application.usecase.GetAccountByIbanQueryImpl;
import com.bank.app.account.application.usecase.GetAccountsByUserQueryImpl;
import com.bank.app.account.application.usecase.AccountQueryUseCaseImpl;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.application.service.UserContextService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AccountBeanConfig {

    @Bean
    public CreateAccountUseCase createAccountUseCase(LoadAccountPort loadAccountPort, SaveAccountPort saveAccountPort,
            DomainEventPublisherService domainEventPublisherService, AuditEventPort auditEventPort, AccountAuthorizationService accountAuthorizationService, ClockProviderPort clockProvider) {
        return new CreateAccountUseCaseImpl(loadAccountPort, saveAccountPort, domainEventPublisherService, auditEventPort, accountAuthorizationService, clockProvider);
    }

    @Bean
    public AccountAuthorizationService accountAuthorizationService(UserContextService userContextService) {
        return new AccountAuthorizationService(userContextService);
    }


    @Bean
    public GetAccountByIdQuery getAccountByIdQuery(LoadAccountPort loadAccountPort,
            AccountAuthorizationService accountAuthorizationService) {
        return new GetAccountByIdQueryImpl(loadAccountPort, accountAuthorizationService);
    }

    @Bean
    public GetAccountByIbanQuery getAccountByIbanQuery(LoadAccountPort loadAccountPort,
            AccountAuthorizationService accountAuthorizationService) {
        return new GetAccountByIbanQueryImpl(loadAccountPort, accountAuthorizationService);
    }

    @Bean
    public GetAccountsByUserQuery getAccountsByUserQuery(LoadAccountPort loadAccountPort,
            AccountAuthorizationService accountAuthorizationService) {
        return new GetAccountsByUserQueryImpl(loadAccountPort, accountAuthorizationService);
    }

    @Bean
    public AccountQueryUseCase accountQueryUseCase(LoadAccountPort loadAccountPort) {
        return new AccountQueryUseCaseImpl(loadAccountPort);
    }

    @Bean
    public AdjustAccountBalancesUseCase adjustAccountBalancesUseCase(LoadAccountPort loadAccountPort,
            SaveAccountPort saveAccountPort, ClockProviderPort clockProvider,
            DomainEventPublisherService domainEventPublisherService) {
        return new AdjustAccountBalancesUseCaseImpl(loadAccountPort, saveAccountPort, clockProvider, domainEventPublisherService);
    }
}
