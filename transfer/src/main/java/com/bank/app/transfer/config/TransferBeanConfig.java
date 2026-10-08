package com.bank.app.transfer.config;

import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.transfer.application.port.in.CancelTransferUseCase;
import com.bank.app.transfer.application.port.in.GenerateTransferReportQuery;
import com.bank.app.transfer.application.port.in.GenerateTransferReportTotalsQuery;
import com.bank.app.transfer.application.port.in.GenerateTransferReportWithTotalsQuery;
import com.bank.app.transfer.application.port.in.GetTransferDetailQuery;
import com.bank.app.transfer.application.port.in.GetTransferHistoryQuery;
import com.bank.app.transfer.application.port.in.PlaceTransferUseCase;
import com.bank.app.accountapi.AccountApi;
import com.bank.app.accountapi.AccountSnapshotCache;
import com.bank.app.transfer.adapter.out.account.AccountAclAdapter;
import com.bank.app.transfer.adapter.out.account.InMemoryAccountInfoCacheAdapter;
import com.bank.app.transfer.application.port.out.AccountAclPort;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
import com.bank.app.transfer.application.service.TransferViewEnricher;
import com.bank.app.transfer.application.usecase.CancelTransferUseCaseImpl;
import com.bank.app.transfer.application.usecase.GenerateTransferReportQueryImpl;
import com.bank.app.transfer.application.usecase.GenerateTransferReportTotalsQueryImpl;
import com.bank.app.transfer.application.usecase.GenerateTransferReportWithTotalsQueryImpl;
import com.bank.app.transfer.application.usecase.GetTransferDetailQueryImpl;
import com.bank.app.transfer.application.usecase.GetTransferHistoryQueryImpl;
import com.bank.app.transfer.application.usecase.PlaceTransferUseCaseImpl;
import com.bank.app.transfer.domain.TransferDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.retry.annotation.EnableRetry;

@Configuration
@EnableRetry
public class TransferBeanConfig {

    private static final Logger log = LoggerFactory.getLogger(TransferBeanConfig.class);

    private final TransferProperties transferProperties;

    public TransferBeanConfig(TransferProperties transferProperties) {
        this.transferProperties = transferProperties;
    }

    @Bean
    public TransferDomainService transferDomainService() {
        return new TransferDomainService();
    }

    /**
     * Single-JVM fallback for dev, test and single-instance use: removes the
     * Redis dependency without changing semantics (invalidation logic is
     * shared via {@code AbstractAccountSnapshotCache}).
     *
     * <p>Never valid with more than one replica: an eviction on one pod never
     * reaches another, so account status/currency reads go stale cluster-wide
     * for up to the TTL. Production pins the shared Redis backend with a
     * literal (not env-overridable) value, guarded by
     * {@code ProductionConfigContractTest} — this fallback must therefore
     * never activate there. The profile check below is defense in depth: if
     * the wiring ever degrades, prod fails fast at startup instead of serving
     * stale snapshots silently.
     */
    @Bean
    @ConditionalOnMissingBean(AccountSnapshotCache.class)
    public AccountSnapshotCache accountInfoCachePort(Environment environment) {
        if (environment.matchesProfiles("prod")) {
            throw new IllegalStateException(
                    "InMemoryAccountInfoCacheAdapter must not run with the prod profile: "
                    + "no shared AccountSnapshotCache backend is registered, so per-JVM "
                    + "caches would go stale across replicas. Fix "
                    + "app.cache.caffeine.account-info.backend=redis instead.");
        }
        log.warn("No shared AccountSnapshotCache backend registered — falling back to "
                + "single-JVM InMemoryAccountInfoCacheAdapter. Correct for dev/test/single "
                + "instance; must never happen with more than one replica.");
        return new InMemoryAccountInfoCacheAdapter();
    }

    @Bean
    public AccountAclPort accountAclPort(AccountApi accountApi, AccountSnapshotCache cache) {
        return new AccountAclAdapter(accountApi, cache);
    }

    @Bean
    public TransferAuthorizationService transferAuthorizationService(AccountAclPort accountAclPort,
                                                                       UserContextService userContextService) {
        return new TransferAuthorizationService(accountAclPort, userContextService);
    }

    @Bean
    public TransferViewEnricher transferViewEnricher(AccountAclPort accountAclPort) {
        return new TransferViewEnricher(accountAclPort);
    }

    @Bean
    public PlaceTransferUseCase placeTransferUseCase(AccountAclPort accountAclPort,
                                                       SaveTransferPort saveTransferPort,
                                                       TransferDomainService transferDomainService,
                                                       TransferAuthorizationService transferAuthorizationService,
                                                       DomainEventPublisherService domainEventPublisherService,
                                                       ClockProviderPort clockProvider,
                                                       AuditEventPort auditEventPort) {
        return new PlaceTransferUseCaseImpl(accountAclPort, saveTransferPort, transferDomainService, transferAuthorizationService, domainEventPublisherService, clockProvider, auditEventPort);
    }

    @Bean
    public CancelTransferUseCase cancelTransferUseCase(LoadTransferPort loadTransferPort,
                                                           SaveTransferPort saveTransferPort,
                                                           AccountAclPort accountAclPort,
                                                           AuditEventPort auditEventPort,
                                                           TransferAuthorizationService transferAuthorizationService,
                                                           DomainEventPublisherService domainEventPublisherService,
                                                           ClockProviderPort clockProvider) {
        return new CancelTransferUseCaseImpl(loadTransferPort, saveTransferPort, accountAclPort, auditEventPort, transferAuthorizationService, domainEventPublisherService, clockProvider, transferProperties.cancellationWindow());
    }

    @Bean
    public GenerateTransferReportQuery generateTransferReportQuery(LoadTransferPort loadTransferPort,
                                                                      TransferViewEnricher viewEnricher,
                                                                      TransferAuthorizationService transferAuthorizationService) {
        return new GenerateTransferReportQueryImpl(loadTransferPort, viewEnricher, transferAuthorizationService, transferProperties.maxPageSize());
    }

    @Bean
    public GenerateTransferReportTotalsQuery generateTransferReportTotalsQuery(LoadTransferPort loadTransferPort,
                                                                              TransferAuthorizationService transferAuthorizationService) {
        return new GenerateTransferReportTotalsQueryImpl(loadTransferPort, transferAuthorizationService);
    }

    @Bean
    public GenerateTransferReportWithTotalsQuery generateTransferReportWithTotalsQuery(
            LoadTransferPort loadTransferPort,
            TransferViewEnricher viewEnricher,
            TransferAuthorizationService transferAuthorizationService) {
        return new GenerateTransferReportWithTotalsQueryImpl(
                loadTransferPort, viewEnricher, transferAuthorizationService, transferProperties.maxPageSize());
    }

    @Bean
    public GetTransferDetailQuery getTransferDetailQuery(LoadTransferPort loadTransferPort,
                                                            AccountAclPort accountAclPort,
                                                            TransferAuthorizationService transferAuthorizationService) {
        return new GetTransferDetailQueryImpl(loadTransferPort, accountAclPort, transferAuthorizationService);
    }

    @Bean
    public GetTransferHistoryQuery getTransferHistoryQuery(LoadTransferPort loadTransferPort,
                                                              TransferViewEnricher viewEnricher,
                                                              TransferAuthorizationService transferAuthorizationService) {
        return new GetTransferHistoryQueryImpl(loadTransferPort, viewEnricher, transferAuthorizationService, transferProperties.maxPageSize());
    }
}
