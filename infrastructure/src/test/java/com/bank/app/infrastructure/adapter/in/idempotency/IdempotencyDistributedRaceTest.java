package com.bank.app.infrastructure.adapter.in.idempotency;

import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.common.domain.ExponentialBackoffPolicy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Long term #1: multi-thread race scenario. With N parallel submissions on the
 * same idempotency key, exactly one must observe NEW while the rest see
 * PENDING/COMPLETED. A real multi-pod test is a K8s chaos job; this test pins
 * the single-JVM contract.
 */
class IdempotencyDistributedRaceTest {

    @Test
    void parallelClaimsOnSameKeyShouldYieldSingleWinner() throws Exception {
        IdempotencyPort port = mock(IdempotencyPort.class);
        AtomicInteger creations = new AtomicInteger();
        when(port.findById(any())).thenReturn(Optional.empty());
        when(port.tryCreate(any(), any(LocalDateTime.class)))
                .thenAnswer(inv -> creations.incrementAndGet() == 1);

        IdempotencyGuard guard = new IdempotencyGuard(port, Clock::systemUTC);
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<IdempotencyGuard.IdempotencyResult>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return guard.startRequest("race-key");
                }));
            }
            start.countDown();
            int news = 0;
            for (Future<IdempotencyGuard.IdempotencyResult> f : futures) {
                if (f.get().status() == IdempotencyGuard.IdempotencyResult.Status.NEW) {
                    news++;
                }
            }
            assertThat(news).as("exactly one parallel claim wins the key").isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void retryPolicyShouldBeDeterministicWithoutSleeping() {
        ExponentialBackoffPolicy policy =
                new ExponentialBackoffPolicy(500, 2000, 0);
        long d0 = policy.initialDelayMs();
        long d1 = policy.nextDelay(d0);
        long d2 = policy.nextDelay(d1);
        assertThat(List.of(d0, d1, d2)).containsExactly(500L, 1000L, 2000L);
    }
}
