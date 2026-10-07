package com.bank.app.audit.config;

import com.bank.app.audit.application.port.in.AuditLoggerUseCase;
import com.bank.app.audit.application.port.in.GetAuditLogsQuery;
import com.bank.app.audit.application.port.out.LoadAuditLogPort;
import com.bank.app.audit.application.port.out.SaveAuditLogPort;
import com.bank.app.audit.application.usecase.AuditLoggerUseCaseImpl;
import com.bank.app.audit.application.usecase.GetAuditLogsQueryImpl;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.service.UserContextService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AuditProperties.class)
public class AuditBeanConfig {

    @Bean
    public AuditLoggerUseCase auditLogger(SaveAuditLogPort saveAuditLogPort,
                                           UserContextService userContextService,
                                           ClockProviderPort clockProvider) {
        return new AuditLoggerUseCaseImpl(saveAuditLogPort, userContextService, clockProvider);
    }

    @Bean
    public GetAuditLogsQuery getAuditLogsQuery(LoadAuditLogPort loadAuditLogPort,
                                               UserContextService userContextService,
                                               AuditProperties auditProperties) {
        return new GetAuditLogsQueryImpl(loadAuditLogPort, userContextService,
                auditProperties.maxQueryLimit());
    }
}
