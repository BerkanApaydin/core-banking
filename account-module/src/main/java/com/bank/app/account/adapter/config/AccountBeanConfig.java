package com.bank.app.account.adapter.config;

import com.bank.app.account.application.port.in.CreateAccountUseCase;
import com.bank.app.account.application.port.in.GetAccountByIdQuery;
import com.bank.app.account.application.port.in.GetAccountByIbanQuery;
import com.bank.app.account.application.port.in.GetAccountsByUserQuery;
import com.bank.app.account.application.port.in.ExecuteTransferUseCase;
import com.bank.app.account.application.port.in.ReverseTransferUseCase;
import com.bank.app.account.application.port.in.AccountQueryUseCase;
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
import com.bank.app.common.application.port.out.security.SecurityContextPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AccountBeanConfig {

    @Bean
    public CreateAccountUseCase createAccountUseCase(LoadAccountPort loadAccountPort, SaveAccountPort saveAccountPort,
            EventPublisherPort eventPublisherPort, SecurityContextPort securityContextPort) {
        return new CreateAccountUseCaseImpl(loadAccountPort, saveAccountPort, eventPublisherPort, securityContextPort);
    }

    @Bean
    public ExecuteTransferUseCase executeTransferUseCase(LoadAccountPort loadAccountPort,
            SaveAccountPort saveAccountPort,
            SecurityContextPort securityContextPort,
            EventPublisherPort eventPublisherPort) {
        return new ExecuteTransferUseCaseImpl(loadAccountPort, saveAccountPort, securityContextPort,
                eventPublisherPort);
    }

    @Bean
    public ReverseTransferUseCase reverseTransferUseCase(LoadAccountPort loadAccountPort,
            SaveAccountPort saveAccountPort,
            SecurityContextPort securityContextPort,
            EventPublisherPort eventPublisherPort) {
        return new ReverseTransferUseCaseImpl(loadAccountPort, saveAccountPort, securityContextPort,
                eventPublisherPort);
    }

    @Bean
    public GetAccountByIdQuery getAccountByIdQuery(LoadAccountPort loadAccountPort,
            SecurityContextPort securityContextPort) {
        return new GetAccountByIdQueryHandler(loadAccountPort, securityContextPort);
    }

    @Bean
    public GetAccountByIbanQuery getAccountByIbanQuery(LoadAccountPort loadAccountPort,
            SecurityContextPort securityContextPort) {
        return new GetAccountByIbanQueryHandler(loadAccountPort, securityContextPort);
    }

    @Bean
    public GetAccountsByUserQuery getAccountsByUserQuery(LoadAccountPort loadAccountPort,
            SecurityContextPort securityContextPort) {
        return new GetAccountsByUserQueryHandler(loadAccountPort, securityContextPort);
    }

    @Bean
    public AccountQueryUseCase accountQueryUseCase(LoadAccountPort loadAccountPort) {
        return new AccountQueryUseCaseImpl(loadAccountPort);
    }
}
