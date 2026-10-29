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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

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

    @Bean(name = "auditBestEffortExecutor")
    public Executor auditBestEffortExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1024);
        executor.setThreadNamePrefix("audit-best-effort-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }
}
