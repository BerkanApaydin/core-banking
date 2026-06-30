package com.bank.app.transfer;

import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.domain.event.DomainEvent;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

@Configuration
@ComponentScan(basePackages = "com.bank.app", excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootApplication.class))
@EntityScan(basePackages = "com.bank.app")
@EnableJpaRepositories(basePackages = "com.bank.app")
@SuppressWarnings("null")
public class ModuleIntegrationTestConfig {

    @Bean
    public EventPublisherPort eventPublisherPort() {
        EventPublisherPort mockPort = mock(EventPublisherPort.class);
        doNothing().when(mockPort).publish(any(DomainEvent.class));
        return mockPort;
    }

    @Bean
    @ConditionalOnMissingBean(DomainEventPublisherService.class)
    public DomainEventPublisherService domainEventPublisherService(EventPublisherPort eventPublisherPort) {
        return new DomainEventPublisherService(eventPublisherPort);
    }
}
