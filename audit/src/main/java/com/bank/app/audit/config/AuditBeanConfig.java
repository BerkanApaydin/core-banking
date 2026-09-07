package com.bank.app.audit.config;

import com.bank.app.audit.application.port.in.AuditLoggerUseCase;
import com.bank.app.audit.application.port.out.SaveAuditLogPort;
import com.bank.app.audit.application.usecase.AuditLoggerUseCaseImpl;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.service.UserContextService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuditBeanConfig {

    @Bean
    public AuditLoggerUseCase auditLogger(SaveAuditLogPort saveAuditLogPort,
                                           UserContextService userContextService,
                                           ClockProviderPort clockProvider) {
        return new AuditLoggerUseCaseImpl(saveAuditLogPort, userContextService, clockProvider);
    }
}
