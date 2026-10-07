package com.bank.app.account.config;

import com.bank.app.account.application.port.in.CreateAccountUseCase;
import com.bank.app.account.application.dto.SimulationFundingCapability;
import com.bank.app.account.application.port.in.AdjustAccountBalancesUseCase;
import com.bank.app.account.application.port.in.SuspendAccountUseCase;
import com.bank.app.account.application.port.in.GetAccountByIdQuery;
import com.bank.app.account.application.port.in.GetAccountByIbanQuery;
import com.bank.app.account.application.port.in.GetAccountsByUserQuery;
import com.bank.app.account.application.port.in.AccountQueryUseCase;
import com.bank.app.account.application.service.AccountAuthorizationService;
import com.bank.app.account.application.usecase.CreateAccountUseCaseImpl;
import com.bank.app.account.application.usecase.AdjustAccountBalancesUseCaseImpl;
import com.bank.app.account.application.usecase.SuspendAccountUseCaseImpl;
import com.bank.app.account.application.usecase.GetAccountByIdQueryImpl;
import com.bank.app.account.application.usecase.GetAccountByIbanQueryImpl;
import com.bank.app.account.application.usecase.GetAccountsByUserQueryImpl;
import com.bank.app.account.application.usecase.AccountQueryUseCaseImpl;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.application.port.out.SaveLedgerPort;
import com.bank.app.account.application.port.out.IbanGeneratorPort;
import com.bank.app.accountapi.AccountSnapshotCache;
import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.application.service.UserContextService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
public class AccountBeanConfig {

    @Bean
    public CreateAccountUseCase createAccountUseCase(LoadAccountPort loadAccountPort, SaveAccountPort saveAccountPort,
            IbanGeneratorPort ibanGeneratorPort,
            DomainEventPublisherService domainEventPublisherService, AuditEventPort auditEventPort,
            AccountAuthorizationService accountAuthorizationService, ClockProviderPort clockProvider,
            SimulationFundingCapability fundingCapability) {
        return new CreateAccountUseCaseImpl(loadAccountPort, saveAccountPort, ibanGeneratorPort, domainEventPublisherService,
                auditEventPort, accountAuthorizationService, clockProvider, fundingCapability.initialFundingEnabled());
    }

    @Bean
    public SimulationFundingCapability simulationFundingCapability(Environment environment) {
        return new SimulationFundingCapability(demoFundingEnabled(environment));
    }

    static boolean demoFundingEnabled(Environment environment) {
        // Hosted simulations retain prod security settings and opt in with the
        // additional simulation profile. Accidental prod+dev stays disabled.
        boolean production = environment.acceptsProfiles(Profiles.of("prod"));
        return production ? environment.acceptsProfiles(Profiles.of("simulation"))
                : environment.acceptsProfiles(Profiles.of("dev", "demo", "test", "testcontainers"));
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
            DomainEventPublisherService domainEventPublisherService, AuditEventPort auditEventPort,
            SaveLedgerPort ledgerPort) {
        return new AdjustAccountBalancesUseCaseImpl(loadAccountPort, saveAccountPort, clockProvider,
                domainEventPublisherService, auditEventPort, ledgerPort);
    }

    @Bean
    public SuspendAccountUseCase suspendAccountUseCase(LoadAccountPort loadAccountPort,
            SaveAccountPort saveAccountPort, UserContextService userContextService,
            ClockProviderPort clockProvider, DomainEventPublisherService domainEventPublisherService,
            AuditEventPort auditEventPort, AccountSnapshotCache snapshotCache) {
        return new SuspendAccountUseCaseImpl(loadAccountPort, saveAccountPort, userContextService,
                clockProvider, domainEventPublisherService, auditEventPort, snapshotCache);
    }
}
