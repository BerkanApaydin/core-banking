package com.bank.app.transfer.config;

import com.bank.app.accountapi.AccountApi;
import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.transfer.application.port.out.AccountAclPort;
import com.bank.app.transfer.application.port.out.AccountInfoCachePort;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
import com.bank.app.transfer.application.service.TransferViewEnricher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class TransferBeanConfigTest {

    @Mock private AccountAclPort accountAclPort;
    @Mock private SaveTransferPort saveTransferPort;
    @Mock private AuditEventPort auditEventPort;
    @Mock private UserContextService userContextService;
    @Mock private LoadTransferPort loadTransferPort;
    @Mock private DomainEventPublisherService domainEventPublisherService;
    @Mock private ClockProviderPort clockProvider;
    @Mock private AccountApi accountApi;
    @Mock private AccountInfoCachePort cachePort;

    private final TransferProperties transferProperties = new TransferProperties(24, 3, 500L, 2000L, 100);

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
                config.transferDomainService(), authService, domainEventPublisherService, clockProvider));
    }

    @Test
    void shouldCreateCancelTransferUseCaseBean() {
        TransferBeanConfig config = new TransferBeanConfig(transferProperties);
        TransferAuthorizationService authService = config.transferAuthorizationService(accountAclPort, userContextService);
        assertNotNull(config.cancelTransferUseCase(loadTransferPort, saveTransferPort,
                accountAclPort, auditEventPort, authService, domainEventPublisherService, clockProvider));
    }

    @Test
    void shouldCreateTransferViewEnricherBean() {
        TransferBeanConfig config = new TransferBeanConfig(transferProperties);
        assertNotNull(config.transferViewEnricher(accountAclPort));
    }

    @Test
    void shouldCreateGenerateTransferReportQueryBean() {
        TransferBeanConfig config = new TransferBeanConfig(transferProperties);
        TransferAuthorizationService authService = config.transferAuthorizationService(accountAclPort, userContextService);
        TransferViewEnricher enricher = config.transferViewEnricher(accountAclPort);
        assertNotNull(config.generateTransferReportQuery(loadTransferPort, enricher, authService));
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
        TransferViewEnricher enricher = config.transferViewEnricher(accountAclPort);
        assertNotNull(config.getTransferHistoryQuery(loadTransferPort, enricher, authService));
    }

    @Test
    void shouldCreateAccountInfoCachePortFallbackBean() {
        TransferBeanConfig config = new TransferBeanConfig(transferProperties);
        assertNotNull(config.accountInfoCachePort());
    }

    @Test
    void shouldCreateAccountAclPortBean() {
        TransferBeanConfig config = new TransferBeanConfig(transferProperties);
        assertNotNull(config.accountAclPort(accountApi, cachePort));
    }

    @Test
    void shouldEnableRetryForNotificationAdapters() {
        // Email/SmsNotificationAdapter carry @Retryable: without @EnableRetry those
        // annotations are silently dead and notifications die on first failure.
        assertTrue(TransferBeanConfig.class.isAnnotationPresent(
                org.springframework.retry.annotation.EnableRetry.class));
    }
}
