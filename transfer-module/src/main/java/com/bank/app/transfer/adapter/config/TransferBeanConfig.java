package com.bank.app.transfer.adapter.config;

import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.application.port.out.security.SecurityContextPort;
import com.bank.app.transfer.application.port.in.CancelTransferUseCase;
import com.bank.app.transfer.application.port.in.GenerateTransferReportQuery;
import com.bank.app.transfer.application.port.in.GetTransferDetailQuery;
import com.bank.app.transfer.application.port.in.GetTransferHistoryQuery;
import com.bank.app.transfer.application.port.in.PlaceTransferUseCase;
import com.bank.app.common.application.port.out.AccountAclPort;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
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
    public PlaceTransferUseCase placeTransferUseCase(AccountAclPort accountAclPort,
                                                       SaveTransferPort saveTransferPort,
                                                       EventPublisherPort eventPublisherPort,
                                                       TransferDomainService transferDomainService,
                                                       SecurityContextPort securityContextPort) {
        return new PlaceTransferUseCaseImpl(accountAclPort, saveTransferPort, eventPublisherPort, transferDomainService, securityContextPort);
    }

    @Bean
    public CancelTransferUseCase cancelTransferUseCase(LoadTransferPort loadTransferPort,
                                                        SaveTransferPort saveTransferPort,
                                                        AccountAclPort accountAclPort,
                                                        EventPublisherPort eventPublisherPort,
                                                        SecurityContextPort securityContextPort) {
        return new CancelTransferUseCaseImpl(loadTransferPort, saveTransferPort, accountAclPort, eventPublisherPort, securityContextPort, transferProperties.cancellationWindowHours());
    }

    @Bean
    public GenerateTransferReportQuery generateTransferReportQuery(LoadTransferPort loadTransferPort,
                                                                     AccountAclPort accountAclPort,
                                                                     SecurityContextPort securityContextPort) {
        return new GenerateTransferReportUseCaseImpl(loadTransferPort, accountAclPort, securityContextPort);
    }

    @Bean
    public GetTransferDetailQuery getTransferDetailQuery(LoadTransferPort loadTransferPort,
                                                           AccountAclPort accountAclPort,
                                                           SecurityContextPort securityContextPort) {
        return new GetTransferDetailUseCaseImpl(loadTransferPort, accountAclPort, securityContextPort);
    }

    @Bean
    public GetTransferHistoryQuery getTransferHistoryQuery(LoadTransferPort loadTransferPort,
                                                             AccountAclPort accountAclPort,
                                                             SecurityContextPort securityContextPort) {
        return new GetTransferHistoryUseCaseImpl(loadTransferPort, accountAclPort, securityContextPort);
    }
}
