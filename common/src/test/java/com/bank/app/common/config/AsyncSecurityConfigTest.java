package com.bank.app.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

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
}
