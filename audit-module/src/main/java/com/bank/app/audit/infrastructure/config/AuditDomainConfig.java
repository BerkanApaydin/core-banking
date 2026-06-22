package com.bank.app.audit.infrastructure.config;

import com.bank.app.audit.application.AuditLogger;
import com.bank.app.audit.application.port.in.AuditLoggerUseCase;
import com.bank.app.audit.application.port.out.SaveAuditLogPort;
import com.bank.app.common.security.port.out.SecurityContextPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuditDomainConfig {

    @Bean
    public AuditLoggerUseCase auditLoggerUseCase(SaveAuditLogPort saveAuditLogPort,
            SecurityContextPort securityContextPort) {
        return new AuditLogger(saveAuditLogPort, securityContextPort);
    }
}
