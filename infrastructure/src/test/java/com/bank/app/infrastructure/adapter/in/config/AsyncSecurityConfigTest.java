package com.bank.app.infrastructure.adapter.in.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
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

        Object result = CompletableFuture.supplyAsync(() -> "executed", executor).get();
        assertEquals("executed", result);
    }

    @Test
    void shouldReuseExistingDelegateOnSecondCall() throws Exception {
        AsyncSecurityConfig config = new AsyncSecurityConfig();
        Executor first = config.asyncTaskExecutor();
        Executor second = config.asyncTaskExecutor();

        assertNotNull(first);
        assertNotNull(second);
        assertSame(extractThreadPoolTaskExecutor(first), extractThreadPoolTaskExecutor(second));
    }

    @Test
    void shouldConfigureThreadPoolProperties() throws Exception {
        AsyncSecurityConfig config = new AsyncSecurityConfig();
        Executor executor = config.asyncTaskExecutor();

        ThreadPoolTaskExecutor delegate = extractThreadPoolTaskExecutor(executor);

        assertEquals(5, delegate.getCorePoolSize());
        assertEquals(20, delegate.getMaxPoolSize());
        assertEquals(500, delegate.getQueueCapacity());
    }

    private static ThreadPoolTaskExecutor extractThreadPoolTaskExecutor(Executor executor) throws Exception {
        Class<?> clazz = executor.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField("delegate");
                field.setAccessible(true);
                Object delegate = field.get(executor);
                if (delegate instanceof ThreadPoolTaskExecutor tte) {
                    return tte;
                }
            } catch (NoSuchFieldException ignored) {}
            clazz = clazz.getSuperclass();
        }
        throw new AssertionError("Could not find delegate field");
    }

    @Test
    void shouldUseCorrectThreadNamePrefix() throws Exception {
        AsyncSecurityConfig config = new AsyncSecurityConfig();
        Executor executor = config.getAsyncExecutor();

        String threadName = CompletableFuture.supplyAsync(() -> Thread.currentThread().getName(), executor).get();
        assertTrue(threadName.startsWith("async-bank-"));
    }

    @Test
    void shouldShutdownDelegate() throws Exception {
        AsyncSecurityConfig config = new AsyncSecurityConfig();
        Executor executor = config.asyncTaskExecutor();
        ThreadPoolTaskExecutor delegate = extractThreadPoolTaskExecutor(executor);
        config.shutdown();
        assertTrue(delegate.getThreadPoolExecutor().isShutdown());
    }

    @Test
    void shouldHandleShutdownWithoutDelegate() {
        AsyncSecurityConfig config = new AsyncSecurityConfig();
        assertDoesNotThrow(config::shutdown);
    }
}
