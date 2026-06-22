package com.bank.app.transfer.infrastructure.config;

import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.security.port.out.SecurityContextPort;
import com.bank.app.transfer.application.port.in.CancelTransferUseCase;
import com.bank.app.transfer.application.port.in.GenerateTransferReportQuery;
import com.bank.app.transfer.application.port.in.GetTransferDetailQuery;
import com.bank.app.transfer.application.port.in.GetTransferHistoryQuery;
import com.bank.app.transfer.application.port.in.PlaceTransferUseCase;
import com.bank.app.transfer.application.port.out.AccountOperationPort;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.application.usecase.CancelTransferUseCaseImpl;
import com.bank.app.transfer.application.usecase.GenerateTransferReportUseCaseImpl;
import com.bank.app.transfer.application.usecase.GetTransferDetailUseCaseImpl;
import com.bank.app.transfer.application.usecase.GetTransferHistoryUseCaseImpl;
import com.bank.app.transfer.application.usecase.PlaceTransferUseCaseImpl;
import com.bank.app.transfer.domain.TransferDomainService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

@Configuration
public class TransferDomainConfig {

    @Value("${app.transfer.cancellation-window-hours}")
    private int cancellationWindowHours;

    @Bean
    @Transactional
    public PlaceTransferUseCase placeTransferUseCase(AccountOperationPort accountOperationPort,
                                                     SaveTransferPort saveTransferPort,
                                                     EventPublisherPort eventPublisherPort,
                                                     TransferDomainService transferDomainService) {
        return new PlaceTransferUseCaseImpl(accountOperationPort, saveTransferPort, eventPublisherPort, transferDomainService);
    }

    @Bean
    @Transactional
    public CancelTransferUseCase cancelTransferUseCase(LoadTransferPort loadTransferPort,
                                                       SaveTransferPort saveTransferPort,
                                                       AccountOperationPort accountOperationPort,
                                                       EventPublisherPort eventPublisherPort,
                                                       SecurityContextPort securityContextPort) {
        return new CancelTransferUseCaseImpl(loadTransferPort, saveTransferPort, accountOperationPort, eventPublisherPort, securityContextPort, cancellationWindowHours);
    }

    @Bean
    public GenerateTransferReportQuery generateTransferReportQuery(LoadTransferPort loadTransferPort,
                                                                   AccountOperationPort accountOperationPort,
                                                                   SecurityContextPort securityContextPort) {
        return new GenerateTransferReportUseCaseImpl(loadTransferPort, accountOperationPort, securityContextPort);
    }

    @Bean
    public GetTransferDetailQuery getTransferDetailQuery(LoadTransferPort loadTransferPort,
                                                         AccountOperationPort accountOperationPort,
                                                         SecurityContextPort securityContextPort) {
        return new GetTransferDetailUseCaseImpl(loadTransferPort, accountOperationPort, securityContextPort);
    }

    @Bean
    public GetTransferHistoryQuery getTransferHistoryQuery(LoadTransferPort loadTransferPort,
                                                           AccountOperationPort accountOperationPort,
                                                           SecurityContextPort securityContextPort) {
        return new GetTransferHistoryUseCaseImpl(loadTransferPort, accountOperationPort, securityContextPort);
    }
}
