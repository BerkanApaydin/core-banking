package com.bank.app.transfer.adapter.in.config;

import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.transfer.application.port.in.CancelTransferUseCase;
import com.bank.app.transfer.application.port.in.GenerateTransferReportQuery;
import com.bank.app.transfer.application.port.in.GetTransferDetailQuery;
import com.bank.app.transfer.application.port.in.GetTransferHistoryQuery;
import com.bank.app.transfer.application.port.in.PlaceTransferUseCase;
import com.bank.app.common.application.port.out.AccountAclPort;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
import com.bank.app.transfer.application.usecase.CancelTransferUseCaseImpl;
import com.bank.app.transfer.application.usecase.GenerateTransferReportUseCaseImpl;
import com.bank.app.transfer.application.usecase.GetTransferDetailUseCaseImpl;
import com.bank.app.transfer.application.usecase.GetTransferHistoryUseCaseImpl;
import com.bank.app.transfer.application.usecase.PlaceTransferUseCaseImpl;
import com.bank.app.transfer.domain.TransferDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TransferBeanConfig {

    private final TransferProperties transferProperties;

    public TransferBeanConfig(TransferProperties transferProperties) {
        this.transferProperties = transferProperties;
    }

    @Bean
    public TransferDomainService transferDomainService() {
        return new TransferDomainService();
    }

    @Bean
    public TransferAuthorizationService transferAuthorizationService(AccountAclPort accountAclPort,
                                                                       UserContextService userContextService) {
        return new TransferAuthorizationService(accountAclPort, userContextService);
    }

    @Bean
    public PlaceTransferUseCase placeTransferUseCase(AccountAclPort accountAclPort,
                                                       SaveTransferPort saveTransferPort,
                                                       TransferDomainService transferDomainService,
                                                       TransferAuthorizationService transferAuthorizationService,
                                                       DomainEventPublisherService domainEventPublisherService) {
        return new PlaceTransferUseCaseImpl(accountAclPort, saveTransferPort, transferDomainService, transferAuthorizationService, domainEventPublisherService);
    }

    @Bean
    public CancelTransferUseCase cancelTransferUseCase(LoadTransferPort loadTransferPort,
                                                          SaveTransferPort saveTransferPort,
                                                          AccountAclPort accountAclPort,
                                                          AuditEventPort auditEventPort,
                                                          TransferAuthorizationService transferAuthorizationService,
                                                          DomainEventPublisherService domainEventPublisherService) {
        return new CancelTransferUseCaseImpl(loadTransferPort, saveTransferPort, accountAclPort, auditEventPort, transferAuthorizationService, domainEventPublisherService, transferProperties.cancellationWindowHours());
    }

    @Bean
    public GenerateTransferReportQuery generateTransferReportQuery(LoadTransferPort loadTransferPort,
                                                                      AccountAclPort accountAclPort,
                                                                      TransferAuthorizationService transferAuthorizationService) {
        return new GenerateTransferReportUseCaseImpl(loadTransferPort, accountAclPort, transferAuthorizationService);
    }

    @Bean
    public GetTransferDetailQuery getTransferDetailQuery(LoadTransferPort loadTransferPort,
                                                            AccountAclPort accountAclPort,
                                                            TransferAuthorizationService transferAuthorizationService) {
        return new GetTransferDetailUseCaseImpl(loadTransferPort, accountAclPort, transferAuthorizationService);
    }

    @Bean
    public GetTransferHistoryQuery getTransferHistoryQuery(LoadTransferPort loadTransferPort,
                                                              AccountAclPort accountAclPort,
                                                              TransferAuthorizationService transferAuthorizationService) {
        return new GetTransferHistoryUseCaseImpl(loadTransferPort, accountAclPort, transferAuthorizationService);
    }
}
