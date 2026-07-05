package com.bank.app.account;

import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.domain.event.DomainEvent;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.test.context.TestConfiguration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doNothing;

@Configuration
@ComponentScan(
    basePackages = "com.bank.app",
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootApplication.class),
        @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = TestConfiguration.class)
    }
)
@EntityScan(basePackages = "com.bank.app")
@EnableJpaRepositories(basePackages = "com.bank.app")
@SuppressWarnings("null")
public class ModuleIntegrationTestConfig {

    @Bean
    @Primary
    public EventPublisherPort domainEventOutboxAdapter() {
        EventPublisherPort mockPort = mock(EventPublisherPort.class);
        doNothing().when(mockPort).publish(any(DomainEvent.class));
        return mockPort;
    }
}
