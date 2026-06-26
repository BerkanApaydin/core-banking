package com.bank.app.account.adapter.config;

import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.application.port.out.security.SecurityContextPort;
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
    @Mock private SecurityContextPort securityContextPort;

    @Test
    void shouldCreateCreateAccountUseCaseBean() {
        AccountBeanConfig config = new AccountBeanConfig();
        assertNotNull(config.createAccountUseCase(loadAccountPort, saveAccountPort, eventPublisherPort, securityContextPort));
    }

    @Test
    void shouldCreateExecuteTransferUseCaseBean() {
        AccountBeanConfig config = new AccountBeanConfig();
        assertNotNull(config.executeTransferUseCase(loadAccountPort, saveAccountPort,
                securityContextPort, eventPublisherPort));
    }

    @Test
    void shouldCreateReverseTransferUseCaseBean() {
        AccountBeanConfig config = new AccountBeanConfig();
        assertNotNull(config.reverseTransferUseCase(loadAccountPort, saveAccountPort,
                securityContextPort, eventPublisherPort));
    }

    @Test
    void shouldCreateGetAccountByIdQueryBean() {
        AccountBeanConfig config = new AccountBeanConfig();
        assertNotNull(config.getAccountByIdQuery(loadAccountPort, securityContextPort));
    }

    @Test
    void shouldCreateGetAccountByIbanQueryBean() {
        AccountBeanConfig config = new AccountBeanConfig();
        assertNotNull(config.getAccountByIbanQuery(loadAccountPort, securityContextPort));
    }

    @Test
    void shouldCreateGetAccountsByUserQueryBean() {
        AccountBeanConfig config = new AccountBeanConfig();
        assertNotNull(config.getAccountsByUserQuery(loadAccountPort, securityContextPort));
    }

    @Test
    void shouldCreateAccountQueryUseCaseBean() {
        AccountBeanConfig config = new AccountBeanConfig();
        assertNotNull(config.accountQueryUseCase(loadAccountPort));
    }
}
