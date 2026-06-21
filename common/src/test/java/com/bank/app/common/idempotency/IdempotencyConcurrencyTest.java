package com.bank.app.common.idempotency;

import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.common.persistence.IdempotencyKeyJpaEntity;
import com.bank.app.common.persistence.IdempotencyKeyJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = com.bank.app.common.TestApplication.class)
class IdempotencyConcurrencyTest extends AbstractSpringBootIntegrationTest {

    @MockBean
    private UserDetailsService userDetailsService;

    @Autowired
    private IdempotencyGuard idempotencyGuard;

    @Autowired
    private IdempotencyKeyJpaRepository repo;

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
        String key = "concurrent-key-" + System.nanoTime();
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger newCount = new AtomicInteger(0);
        AtomicInteger pendingCount = new AtomicInteger(0);
        AtomicInteger completedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    runInNewTx(() -> {
                        IdempotencyGuard.IdempotencyResult result = idempotencyGuard.startRequest(key);
                        switch (result.status()) {
                            case NEW -> newCount.incrementAndGet();
                            case PENDING -> pendingCount.incrementAndGet();
                            case COMPLETED -> completedCount.incrementAndGet();
                        }
                    });
                } catch (Exception ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        latch.countDown();
        finishLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, newCount.get(), "Only one thread should get NEW status");
        int otherCount = pendingCount.get() + completedCount.get();
        assertEquals(threadCount - 1, otherCount,
                "All other threads should get PENDING or COMPLETED");

        repo.deleteById(key);
    }

    @Test
    void shouldNotDuplicateCompletedRequestsUnderConcurrentAccess() throws InterruptedException {
        String key = "concurrent-complete-" + System.nanoTime();
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);

        runInNewTx(() -> {
            repo.save(new IdempotencyKeyJpaEntity(key, "PENDING", null, LocalDateTime.now()));
        });

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    runInNewTx(() -> {
                        repo.findById(key).ifPresent(entity -> {
                            if ("PENDING".equals(entity.getStatus())) {
                                entity.setStatus("COMPLETED");
                                entity.setResponseBody("result-" + System.nanoTime());
                                repo.save(entity);
                                successCount.incrementAndGet();
                            }
                        });
                    });
                } catch (Exception ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        latch.countDown();
        finishLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, successCount.get(),
                "Only one thread should successfully transition PENDING -> COMPLETED");

        IdempotencyKeyJpaEntity entity = repo.findById(key).orElseThrow();
        assertEquals("COMPLETED", entity.getStatus());
        assertTrue(entity.getResponseBody().startsWith("result-"));

        repo.deleteById(key);
    }
}
