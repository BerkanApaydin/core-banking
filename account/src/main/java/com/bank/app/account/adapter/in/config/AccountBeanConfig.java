package com.bank.app.account.adapter.in.config;

import com.bank.app.account.application.port.in.CreateAccountUseCase;
import com.bank.app.account.application.port.in.GetAccountByIdQuery;
import com.bank.app.account.application.port.in.GetAccountByIbanQuery;
import com.bank.app.account.application.port.in.GetAccountsByUserQuery;
import com.bank.app.account.application.port.in.ExecuteTransferUseCase;
import com.bank.app.account.application.port.in.ReverseTransferUseCase;
import com.bank.app.account.application.port.in.AccountQueryUseCase;
import com.bank.app.account.application.service.AccountAuthorizationService;
import com.bank.app.account.application.usecase.CreateAccountUseCaseImpl;
import com.bank.app.account.application.usecase.GetAccountByIdQueryHandler;
import com.bank.app.account.application.usecase.GetAccountByIbanQueryHandler;
import com.bank.app.account.application.usecase.GetAccountsByUserQueryHandler;
import com.bank.app.account.application.usecase.ExecuteTransferUseCaseImpl;
import com.bank.app.account.application.usecase.ReverseTransferUseCaseImpl;
import com.bank.app.account.application.usecase.AccountQueryUseCaseImpl;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.application.service.UserContextService;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class AccountBeanConfig {

    @Bean
    public CreateAccountUseCase createAccountUseCase(LoadAccountPort loadAccountPort, SaveAccountPort saveAccountPort,
            EventPublisherPort eventPublisherPort, AccountAuthorizationService accountAuthorizationService) {
        return new CreateAccountUseCaseImpl(loadAccountPort, saveAccountPort, eventPublisherPort, accountAuthorizationService);
    }

    @Bean
    public AccountAuthorizationService accountAuthorizationService(UserContextService userContextService) {
        return new AccountAuthorizationService(userContextService);
    }

    @Bean
    public ExecuteTransferUseCase executeTransferUseCase(LoadAccountPort loadAccountPort,
            SaveAccountPort saveAccountPort,
            AccountAuthorizationService accountAuthorizationService,
            EventPublisherPort eventPublisherPort) {
        return new ExecuteTransferUseCaseImpl(loadAccountPort, saveAccountPort, accountAuthorizationService,
                eventPublisherPort);
    }

    @Bean
    public ReverseTransferUseCase reverseTransferUseCase(LoadAccountPort loadAccountPort,
            SaveAccountPort saveAccountPort,
            AccountAuthorizationService accountAuthorizationService,
            EventPublisherPort eventPublisherPort) {
        return new ReverseTransferUseCaseImpl(loadAccountPort, saveAccountPort, accountAuthorizationService,
                eventPublisherPort);
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
