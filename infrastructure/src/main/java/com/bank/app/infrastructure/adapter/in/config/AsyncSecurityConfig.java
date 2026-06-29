package com.bank.app.infrastructure.adapter.in.config;

import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncSecurityConfig implements AsyncConfigurer {

    private ThreadPoolTaskExecutor delegate;

    @Override
    public Executor getAsyncExecutor() {
        return asyncTaskExecutor();
    }

    @Bean(name = "asyncTaskExecutor")
    public Executor asyncTaskExecutor() {
        if (delegate == null) {
            delegate = new ThreadPoolTaskExecutor();
            delegate.setCorePoolSize(5);
            delegate.setMaxPoolSize(20);
            delegate.setQueueCapacity(500);
            delegate.setThreadNamePrefix("async-bank-");
            delegate.setWaitForTasksToCompleteOnShutdown(true);
            delegate.setAwaitTerminationSeconds(30);
            delegate.initialize();
        }
        return new DelegatingSecurityContextAsyncTaskExecutor(delegate);
    }

    @PreDestroy
    public void shutdown() {
        if (delegate != null) {
            delegate.shutdown();
        }
    }
}
