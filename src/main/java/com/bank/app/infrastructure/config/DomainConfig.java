package com.bank.app.infrastructure.config;

import com.bank.app.transfer.domain.TransferDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    @Bean
    public TransferDomainService transferDomainService() {
        return new TransferDomainService();
    }
}
