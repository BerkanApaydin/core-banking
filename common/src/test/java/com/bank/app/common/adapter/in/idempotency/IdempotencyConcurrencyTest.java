package com.bank.app.common.adapter.in.idempotency;

import com.bank.app.common.AbstractSpringBootIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("null")
@SpringBootTest(classes = com.bank.app.common.TestApplication.class)
class IdempotencyConcurrencyTest extends AbstractSpringBootIntegrationTest {

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Autowired
    private IdempotencyGuard idempotencyGuard;

    @Autowired
    private PlatformTransactionManager transactionManager;

    void runInNewTx(Runnable action) {
        var template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.execute(status -> {
            action.run();
            return null;
        });
    }

    @Test
    void shouldHandleConcurrentStartRequestForSameKey() throws InterruptedException {
        String key = UUID.randomUUID().toString();
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger newCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    IdempotencyGuard.IdempotencyResult result = idempotencyGuard.startRequest(key);
                    if (result.status() == IdempotencyGuard.IdempotencyResult.Status.NEW) {
                        newCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        latch.countDown();
        assertTrue(finishLatch.await(30, TimeUnit.SECONDS), "All threads should finish");
        executor.shutdown();

        assertEquals(1, newCount.get(), "Exactly one thread should get NEW status");

        Thread.sleep(500);

        IdempotencyGuard.IdempotencyResult result = idempotencyGuard.startRequest(key);
        assertEquals(IdempotencyGuard.IdempotencyResult.Status.PENDING, result.status(),
                "Key should still be in PENDING state after concurrent start");

        idempotencyGuard.failRequest(key);
    }

    @Test
    void shouldNotDuplicateCompletedRequestsUnderConcurrentAccess() throws InterruptedException {
        String key = UUID.randomUUID().toString();
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger newCount = new AtomicInteger(0);
        AtomicInteger completedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    IdempotencyGuard.IdempotencyResult result = idempotencyGuard.startRequest(key);
                    if (result.status() == IdempotencyGuard.IdempotencyResult.Status.NEW) {
                        newCount.incrementAndGet();
                        idempotencyGuard.completeRequest(key, "result-" + UUID.randomUUID(), 200);
                    } else if (result.status() == IdempotencyGuard.IdempotencyResult.Status.COMPLETED) {
                        completedCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        latch.countDown();
        assertTrue(finishLatch.await(30, TimeUnit.SECONDS), "All threads should finish");
        executor.shutdown();

        assertEquals(1, newCount.get(), "Only one thread should start the request");

        Thread.sleep(1000);

        IdempotencyGuard.IdempotencyResult finalResult = idempotencyGuard.startRequest(key);
        assertEquals(IdempotencyGuard.IdempotencyResult.Status.COMPLETED, finalResult.status(),
                "Final state should be COMPLETED");
        assertTrue(finalResult.responseBody().startsWith("result-"));

        idempotencyGuard.failRequest(key);
    }

}