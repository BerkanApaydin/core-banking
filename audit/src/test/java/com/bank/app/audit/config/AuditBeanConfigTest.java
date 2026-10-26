package com.bank.app.audit.config;

import com.bank.app.audit.application.port.out.LoadAuditLogPort;
import com.bank.app.common.application.service.UserContextService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class AuditBeanConfigTest {

    @Mock
    private LoadAuditLogPort loadAuditLogPort;

    @Mock
    private UserContextService userContextService;

    @Test
    void shouldCreateGetAuditLogsQueryBean() {
        AuditBeanConfig config = new AuditBeanConfig();
        assertNotNull(config.getAuditLogsQuery(loadAuditLogPort, userContextService,
                new AuditProperties(500, true, 365)));
    }
}
