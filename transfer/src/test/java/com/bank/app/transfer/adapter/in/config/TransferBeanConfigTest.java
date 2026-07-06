package com.bank.app.transfer.adapter.in.config;

import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.common.application.port.out.AccountAclPort;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class TransferBeanConfigTest {

    @Mock private AccountAclPort accountAclPort;
    @Mock private SaveTransferPort saveTransferPort;
    @Mock private AuditEventPort auditEventPort;
    @Mock private UserContextService userContextService;
    @Mock private LoadTransferPort loadTransferPort;
    @Mock private DomainEventPublisherService domainEventPublisherService;

    private final TransferProperties transferProperties = new TransferProperties(24, 3, 500L, 2000L);

    @Test
    void shouldCreateTransferDomainServiceBean() {
        TransferBeanConfig config = new TransferBeanConfig(transferProperties);
        assertNotNull(config.transferDomainService());
    }

    @Test
    void shouldCreateTransferAuthorizationServiceBean() {
        TransferBeanConfig config = new TransferBeanConfig(transferProperties);
        assertNotNull(config.transferAuthorizationService(accountAclPort, userContextService));
    }

    @Test
    void shouldCreatePlaceTransferUseCaseBean() {
        TransferBeanConfig config = new TransferBeanConfig(transferProperties);
        TransferAuthorizationService authService = config.transferAuthorizationService(accountAclPort, userContextService);
        assertNotNull(config.placeTransferUseCase(accountAclPort, saveTransferPort,
                config.transferDomainService(), authService, domainEventPublisherService));
    }

    @Test
    void shouldCreateCancelTransferUseCaseBean() {
        TransferBeanConfig config = new TransferBeanConfig(transferProperties);
        TransferAuthorizationService authService = config.transferAuthorizationService(accountAclPort, userContextService);
        assertNotNull(config.cancelTransferUseCase(loadTransferPort, saveTransferPort,
                accountAclPort, auditEventPort, authService, domainEventPublisherService));
    }

    @Test
    void shouldCreateGenerateTransferReportQueryBean() {
        TransferBeanConfig config = new TransferBeanConfig(transferProperties);
        TransferAuthorizationService authService = config.transferAuthorizationService(accountAclPort, userContextService);
        assertNotNull(config.generateTransferReportQuery(loadTransferPort, accountAclPort, authService));
    }

    @Test
    void shouldCreateGetTransferDetailQueryBean() {
        TransferBeanConfig config = new TransferBeanConfig(transferProperties);
        TransferAuthorizationService authService = config.transferAuthorizationService(accountAclPort, userContextService);
        assertNotNull(config.getTransferDetailQuery(loadTransferPort, accountAclPort, authService));
    }

    @Test
    void shouldCreateGetTransferHistoryQueryBean() {
        TransferBeanConfig config = new TransferBeanConfig(transferProperties);
        TransferAuthorizationService authService = config.transferAuthorizationService(accountAclPort, userContextService);
        assertNotNull(config.getTransferHistoryQuery(loadTransferPort, accountAclPort, authService));
    }
}
