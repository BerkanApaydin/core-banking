package com.bank.app.infrastructure.config;

import com.bank.app.transfer.domain.TransferDomainService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DomainConfigTest {

    @Test
    void shouldCreateTransferDomainServiceBean() {
        DomainConfig config = new DomainConfig();
        TransferDomainService service = config.transferDomainService();
        assertNotNull(service);
    }
}
