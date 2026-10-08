package com.bank.app.transfer.config;

import com.bank.app.accountapi.AccountApi;
import com.bank.app.accountapi.AccountSnapshotCache;
import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.transfer.application.port.in.GenerateTransferReportWithTotalsQuery;
import com.bank.app.transfer.application.port.out.AccountAclPort;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
import com.bank.app.transfer.application.service.TransferViewEnricher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.retry.annotation.EnableRetry;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    @Mock private AccountSnapshotCache cachePort;

    private final TransferProperties transferProperties = new TransferProperties(
            Duration.ofHours(24), 3, 500L, 2000L, 100);

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
                config.transferDomainService(), authService, domainEventPublisherService, clockProvider, auditEventPort));
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
    void shouldCreateGenerateTransferReportTotalsQueryBean() {
        TransferBeanConfig config = new TransferBeanConfig(transferProperties);
        TransferAuthorizationService authService = config.transferAuthorizationService(accountAclPort, userContextService);
        assertNotNull(config.generateTransferReportTotalsQuery(loadTransferPort, authService));
    }

    @Test
    void shouldCreateGenerateTransferReportWithTotalsQueryBean() {
        TransferBeanConfig config = new TransferBeanConfig(transferProperties);
        TransferAuthorizationService authService = config.transferAuthorizationService(accountAclPort, userContextService);
        TransferViewEnricher enricher = config.transferViewEnricher(accountAclPort);
        assertTrue(config.generateTransferReportWithTotalsQuery(loadTransferPort, enricher, authService)
                instanceof GenerateTransferReportWithTotalsQuery);
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
        Environment environment = mock(Environment.class);
        assertNotNull(config.accountInfoCachePort(environment));
    }

    @Test
    void shouldFailFastWhenFallbackWouldRunUnderProd() {
        // Defense in depth: the prod profile pins the shared Redis backend via
        // ProductionConfigContractTest, so this fallback must never activate
        // there. If the wiring ever degrades, fail at startup instead of
        // serving per-JVM snapshots silently across replicas.
        TransferBeanConfig config = new TransferBeanConfig(transferProperties);
        Environment prod = mock(Environment.class);
        when(prod.matchesProfiles("prod")).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> config.accountInfoCachePort(prod));
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
                EnableRetry.class));
    }
}
