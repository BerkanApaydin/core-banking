package com.bank.app.infrastructure.adapter.in.config;

import com.bank.app.common.application.port.out.SecurityContextPort;
import com.bank.app.common.application.service.UserContextService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommonBeanConfig {

    @Bean
    public UserContextService userContextService(SecurityContextPort securityContextPort) {
        return new UserContextService(securityContextPort);
    }
}
