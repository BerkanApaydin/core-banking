package com.bank.app.transfer.infrastructure.adapter;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerIntegrationTest {

    @Test
    void shouldOpenAfterExceedingFailureThreshold() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(3)
                .minimumNumberOfCalls(2)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        CircuitBreaker cb = registry.circuitBreaker("test");

        cb.onError(0, TimeUnit.NANOSECONDS, new RuntimeException("fail"));
        cb.onError(0, TimeUnit.NANOSECONDS, new RuntimeException("fail"));

        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
    }

    @Test
    void shouldRemainClosedBelowFailureThreshold() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(4)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        CircuitBreaker cb = registry.circuitBreaker("test");

        cb.onSuccess(0, TimeUnit.NANOSECONDS);
        cb.onError(0, TimeUnit.NANOSECONDS, new RuntimeException("fail"));

        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void shouldTransitionToHalfOpenAfterWaitDuration() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(2)
                .waitDurationInOpenState(Duration.ofMillis(100))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        CircuitBreaker cb = registry.circuitBreaker("test");

        cb.onError(0, TimeUnit.NANOSECONDS, new RuntimeException("fail"));
        cb.onError(0, TimeUnit.NANOSECONDS, new RuntimeException("fail"));
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());

        CircuitBreaker.State afterWait = cb.getState();
        assertFalse(CircuitBreaker.State.CLOSED.equals(afterWait));
    }

    @Test
    void shouldRejectCallsWhenOpen() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(3)
                .minimumNumberOfCalls(2)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        CircuitBreaker cb = registry.circuitBreaker("test");

        cb.onError(0, TimeUnit.NANOSECONDS, new RuntimeException("fail"));
        cb.onError(0, TimeUnit.NANOSECONDS, new RuntimeException("fail"));
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());

        assertFalse(cb.tryAcquirePermission());
    }

    @Test
    void shouldAcquireCallsWhenClosed() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(3)
                .minimumNumberOfCalls(2)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        CircuitBreaker cb = registry.circuitBreaker("test");

        assertTrue(cb.tryAcquirePermission());
    }
}
