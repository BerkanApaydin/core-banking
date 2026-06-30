package com.bank.app.audit.adapter.in.config;

import com.bank.app.audit.application.port.in.AuditLoggerUseCase;
import com.bank.app.audit.application.port.out.SaveAuditLogPort;
import com.bank.app.common.application.service.UserContextService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class AuditBeanConfigTest {

    @Mock
    private SaveAuditLogPort saveAuditLogPort;

    @Mock
    private UserContextService userContextService;

    @Test
    void shouldCreateAuditLoggerBean() {
        AuditBeanConfig config = new AuditBeanConfig();
        AuditLoggerUseCase useCase = config.auditLogger(saveAuditLogPort, userContextService);
        assertNotNull(useCase);
    }
}
