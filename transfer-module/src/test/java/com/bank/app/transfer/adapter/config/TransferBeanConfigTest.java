package com.bank.app.transfer.adapter.config;

import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.application.port.out.security.SecurityContextPort;
import com.bank.app.common.application.port.out.AccountAclPort;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class TransferBeanConfigTest {

    @Mock private AccountAclPort accountAclPort;
    @Mock private SaveTransferPort saveTransferPort;
    @Mock private EventPublisherPort eventPublisherPort;
    @Mock private SecurityContextPort securityContextPort;
    @Mock private LoadTransferPort loadTransferPort;

    private final TransferProperties transferProperties = new TransferProperties(24);

    @Test
    void shouldCreateTransferDomainServiceBean() {
        TransferBeanConfig config = new TransferBeanConfig(transferProperties);
        assertNotNull(config.transferDomainService());
    }

    @Test
    void shouldCreatePlaceTransferUseCaseBean() {
        TransferBeanConfig config = new TransferBeanConfig(transferProperties);
        assertNotNull(config.placeTransferUseCase(accountAclPort, saveTransferPort,
                eventPublisherPort, config.transferDomainService(), securityContextPort));
    }

    @Test
    void shouldCreateCancelTransferUseCaseBean() {
        TransferBeanConfig config = new TransferBeanConfig(transferProperties);
        assertNotNull(config.cancelTransferUseCase(loadTransferPort, saveTransferPort,
                accountAclPort, eventPublisherPort, securityContextPort));
    }

    @Test
    void shouldCreateGenerateTransferReportQueryBean() {
        TransferBeanConfig config = new TransferBeanConfig(transferProperties);
        assertNotNull(config.generateTransferReportQuery(loadTransferPort, accountAclPort, securityContextPort));
    }

    @Test
    void shouldCreateGetTransferDetailQueryBean() {
        TransferBeanConfig config = new TransferBeanConfig(transferProperties);
        assertNotNull(config.getTransferDetailQuery(loadTransferPort, accountAclPort, securityContextPort));
    }

    @Test
    void shouldCreateGetTransferHistoryQueryBean() {
        TransferBeanConfig config = new TransferBeanConfig(transferProperties);
        assertNotNull(config.getTransferHistoryQuery(loadTransferPort, accountAclPort, securityContextPort));
    }
}
