package com.bank.app.infrastructure.adapter.in.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AsyncSecurityConfigTest {

    @Test
    void shouldCreateDelegatingSecurityContextExecutor() {
        AsyncSecurityConfig config = new AsyncSecurityConfig();
        Executor executor = config.getAsyncExecutor();

        assertNotNull(executor);
        assertInstanceOf(DelegatingSecurityContextAsyncTaskExecutor.class, executor);

        DelegatingSecurityContextAsyncTaskExecutor delegating = (DelegatingSecurityContextAsyncTaskExecutor) executor;
        assertNotNull(delegating);
    }

    @Test
    void shouldSubmitAndExecuteTask() throws Exception {
        AsyncSecurityConfig config = new AsyncSecurityConfig();
        Executor executor = config.getAsyncExecutor();

        assertNotNull(executor);

        Object result = java.util.concurrent.CompletableFuture.supplyAsync(() -> "executed", executor).get();
        assertEquals("executed", result);
    }

    @Test
    void shouldReuseExistingDelegateOnSecondCall() {
        AsyncSecurityConfig config = new AsyncSecurityConfig();
        Executor first = config.asyncTaskExecutor();
        Executor second = config.asyncTaskExecutor();

        assertNotNull(first);
        assertNotNull(second);
    }

    @Test
    void shouldShutdownDelegate() {
        AsyncSecurityConfig config = new AsyncSecurityConfig();
        config.asyncTaskExecutor();
        config.shutdown();
    }
}
