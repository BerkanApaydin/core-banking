package com.bank.app.infrastructure.config;

import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.application.port.out.IdGeneratorPort;
import com.bank.app.common.application.port.out.SecurityContextPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.infrastructure.adapter.out.clock.SystemClockProvider;
import com.bank.app.infrastructure.adapter.out.id.UuidIdGeneratorAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class CommonBeanConfig {

    @Bean
    public UserContextService userContextService(SecurityContextPort securityContextPort) {
        return new UserContextService(securityContextPort);
    }

    @Bean
    public ClockProviderPort clockProvider() {
        return new SystemClockProvider(Clock.systemUTC());
    }

    @Bean
    public IdGeneratorPort idGeneratorPort() {
        return new UuidIdGeneratorAdapter();
    }

    @Bean
    @ConditionalOnBean(EventPublisherPort.class)
    public DomainEventPublisherService domainEventPublisherService(EventPublisherPort eventPublisherPort) {
        return new DomainEventPublisherService(eventPublisherPort);
    }
}
