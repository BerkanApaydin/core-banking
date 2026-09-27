package com.bank.app.account.config;

import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.application.port.out.IbanGeneratorPort;
import com.bank.app.account.application.service.AccountAuthorizationService;
import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.application.service.UserContextService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class AccountBeanConfigTest {

    @Mock private LoadAccountPort loadAccountPort;
    @Mock private SaveAccountPort saveAccountPort;
    @Mock private IbanGeneratorPort ibanGeneratorPort;
    @Mock private DomainEventPublisherService domainEventPublisherService;
    @Mock private AuditEventPort auditEventPort;
    @Mock private UserContextService userContextService;
    @Mock private AccountAuthorizationService accountAuthorizationService;
    @Mock private ClockProviderPort clockProvider;

    @Test
    void shouldCreateAccountAuthorizationServiceBean() {
        AccountBeanConfig config = new AccountBeanConfig();
        assertNotNull(config.accountAuthorizationService(userContextService));
    }

    @Test
    void shouldCreateCreateAccountUseCaseBean() {
        AccountBeanConfig config = new AccountBeanConfig();
        AccountAuthorizationService authService = config.accountAuthorizationService(userContextService);
        assertNotNull(config.createAccountUseCase(loadAccountPort, saveAccountPort, ibanGeneratorPort, domainEventPublisherService,
                auditEventPort, authService, clockProvider,
                config.simulationFundingCapability(new MockEnvironment())));
    }

    @Test
    void onlyExplicitLocalOrHostedSimulationProfilesPermitDemoFunding() {
        var dev = new MockEnvironment();
        dev.setActiveProfiles("dev");
        var testcontainers = new MockEnvironment();
        testcontainers.setActiveProfiles("testcontainers");
        var mixed = new MockEnvironment();
        mixed.setActiveProfiles("dev", "prod");
        var hostedSimulation = new MockEnvironment();
        hostedSimulation.setActiveProfiles("prod", "simulation");

        assertFalse(AccountBeanConfig.demoFundingEnabled(new MockEnvironment()));
        assertTrue(AccountBeanConfig.demoFundingEnabled(dev));
        assertTrue(AccountBeanConfig.demoFundingEnabled(testcontainers));
        assertFalse(AccountBeanConfig.demoFundingEnabled(mixed));
        assertTrue(AccountBeanConfig.demoFundingEnabled(hostedSimulation));
    }


    @Test
    void shouldCreateGetAccountByIdQueryBean() {
        AccountBeanConfig config = new AccountBeanConfig();
        assertNotNull(config.getAccountByIdQuery(loadAccountPort, accountAuthorizationService));
    }

    @Test
    void shouldCreateGetAccountByIbanQueryBean() {
        AccountBeanConfig config = new AccountBeanConfig();
        assertNotNull(config.getAccountByIbanQuery(loadAccountPort, accountAuthorizationService));
    }

    @Test
    void shouldCreateGetAccountsByUserQueryBean() {
        AccountBeanConfig config = new AccountBeanConfig();
        assertNotNull(config.getAccountsByUserQuery(loadAccountPort, accountAuthorizationService));
    }

    @Test
    void shouldCreateAccountQueryUseCaseBean() {
        AccountBeanConfig config = new AccountBeanConfig();
        assertNotNull(config.accountQueryUseCase(loadAccountPort));
    }

    @Test
    void shouldCreateAdjustAccountBalancesUseCaseBean() {
        AccountBeanConfig config = new AccountBeanConfig();
        assertNotNull(config.adjustAccountBalancesUseCase(loadAccountPort, saveAccountPort, clockProvider, domainEventPublisherService));
    }
}
