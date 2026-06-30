package com.bank.app.account.adapter.in.config;

import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.application.service.AccountAuthorizationService;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.application.service.UserContextService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class AccountBeanConfigTest {

    @Mock private LoadAccountPort loadAccountPort;
    @Mock private SaveAccountPort saveAccountPort;
    @Mock private EventPublisherPort eventPublisherPort;
    @Mock private UserContextService userContextService;
    @Mock private AccountAuthorizationService accountAuthorizationService;

    @Test
    void shouldCreateAccountAuthorizationServiceBean() {
        AccountBeanConfig config = new AccountBeanConfig();
        assertNotNull(config.accountAuthorizationService(userContextService));
    }

    @Test
    void shouldCreateCreateAccountUseCaseBean() {
        AccountBeanConfig config = new AccountBeanConfig();
        AccountAuthorizationService authService = config.accountAuthorizationService(userContextService);
        assertNotNull(config.createAccountUseCase(loadAccountPort, saveAccountPort, eventPublisherPort, authService));
    }

    @Test
    void shouldCreateExecuteTransferUseCaseBean() {
        AccountBeanConfig config = new AccountBeanConfig();
        AccountAuthorizationService authService = config.accountAuthorizationService(userContextService);
        assertNotNull(config.executeTransferUseCase(loadAccountPort, saveAccountPort,
                authService, eventPublisherPort));
    }

    @Test
    void shouldCreateReverseTransferUseCaseBean() {
        AccountBeanConfig config = new AccountBeanConfig();
        AccountAuthorizationService authService = config.accountAuthorizationService(userContextService);
        assertNotNull(config.reverseTransferUseCase(loadAccountPort, saveAccountPort,
                authService, eventPublisherPort));
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
}
