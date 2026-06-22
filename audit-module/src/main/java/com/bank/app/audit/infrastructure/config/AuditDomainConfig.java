package com.bank.app.audit.infrastructure.config;

import com.bank.app.audit.application.AuditLogger;
import com.bank.app.audit.application.port.in.AuditLoggerUseCase;
import com.bank.app.audit.application.port.out.SaveAuditLogPort;
import com.bank.app.audit.infrastructure.decorator.AuditLoggerTransactionDecorator;
import com.bank.app.common.security.port.out.SecurityContextPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AuditDomainConfig {

    @Bean
    @Qualifier("rawAuditLogger")
    public AuditLoggerUseCase rawAuditLogger(SaveAuditLogPort saveAuditLogPort,
                                              SecurityContextPort securityContextPort) {
        return new AuditLogger(saveAuditLogPort, securityContextPort);
    }

    @Bean
    @Primary
    public AuditLoggerTransactionDecorator auditLogger(
            @Qualifier("rawAuditLogger") AuditLoggerUseCase auditLoggerUseCase) {
        return new AuditLoggerTransactionDecorator(auditLoggerUseCase);
    }
}
