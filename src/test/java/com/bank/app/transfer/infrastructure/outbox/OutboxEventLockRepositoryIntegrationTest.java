package com.bank.app.transfer.infrastructure.outbox;

import com.bank.app.common.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Import(OutboxEventLockRepository.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxEventLockRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxEventLockRepository lockRepository;

    @Autowired
    private SpringDataOutboxEventRepo outboxRepo;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        outboxRepo.deleteAll();
    }

    @Test
    void shouldReturnEmptyListWhenNoUnprocessedEvents() {
        List<OutboxEventJpaEntity> result = lockRepository.findAndLockUnprocessed(10);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFindAndLockUnprocessedEventsWithSkipLocked() {
        outboxRepo.save(new OutboxEventJpaEntity(
                "evt-1", "Transfer", "1", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, null));

        List<OutboxEventJpaEntity> result = lockRepository.findAndLockUnprocessed(10);
        assertEquals(1, result.size());
        assertEquals("evt-1", result.get(0).getId());
    }

    @Test
    void shouldExcludeProcessedEvents() {
        outboxRepo.save(new OutboxEventJpaEntity(
                "evt-1", "Transfer", "1", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), true, LocalDateTime.now()));
        outboxRepo.save(new OutboxEventJpaEntity(
                "evt-2", "Transfer", "2", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, null));

        List<OutboxEventJpaEntity> result = lockRepository.findAndLockUnprocessed(10);
        assertEquals(1, result.size());
        assertEquals("evt-2", result.get(0).getId());
    }

    @Test
    void shouldExcludeDeadLetterEvents() {
        outboxRepo.save(new OutboxEventJpaEntity(
                "evt-1", "Transfer", "1", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, null,
                5, true, "dead"));
        outboxRepo.save(new OutboxEventJpaEntity(
                "evt-2", "Transfer", "2", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, null));

        List<OutboxEventJpaEntity> result = lockRepository.findAndLockUnprocessed(10);
        assertEquals(1, result.size());
        assertEquals("evt-2", result.get(0).getId());
    }

    @Test
    void shouldOrderByCreatedAtAscending() {
        outboxRepo.save(new OutboxEventJpaEntity(
                "evt-later", "Transfer", "1", "TransferCompletedEvent",
                "{}", LocalDateTime.now().plusMinutes(5), false, null));
        outboxRepo.save(new OutboxEventJpaEntity(
                "evt-earlier", "Transfer", "2", "TransferCompletedEvent",
                "{}", LocalDateTime.now().minusMinutes(5), false, null));

        List<OutboxEventJpaEntity> result = lockRepository.findAndLockUnprocessed(10);
        assertEquals(2, result.size());
        assertEquals("evt-earlier", result.get(0).getId());
        assertEquals("evt-later", result.get(1).getId());
    }

    @Test
    void shouldRespectLimit() {
        for (int i = 0; i < 5; i++) {
            outboxRepo.save(new OutboxEventJpaEntity(
                    "evt-" + i, "Transfer", String.valueOf(i), "TransferCompletedEvent",
                    "{}", LocalDateTime.now().plusSeconds(i), false, null));
        }

        List<OutboxEventJpaEntity> result = lockRepository.findAndLockUnprocessed(3);
        assertEquals(3, result.size());
    }

    @Test
    void shouldHandleConcurrentSkipLockedAccess() throws InterruptedException {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            for (int i = 0; i < 10; i++) {
                outboxRepo.save(new OutboxEventJpaEntity(
                        "evt-" + i, "Transfer", String.valueOf(i), "TransferCompletedEvent",
                        "{}", LocalDateTime.now().plusSeconds(i), false, null));
            }
        });

        int threadCount = 3;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger totalProcessed = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            new Thread(() -> {
                List<OutboxEventJpaEntity> batch = tx.execute(status -> {
                    List<OutboxEventJpaEntity> locked = lockRepository.findAndLockUnprocessed(5);
                    locked.forEach(e -> {
                        e.setProcessed(true);
                        e.setProcessedAt(LocalDateTime.now());
                        outboxRepo.save(e);
                    });
                    return locked;
                });
                totalProcessed.addAndGet(batch.size());
                latch.countDown();
            }).start();
        }

        latch.await();
        assertEquals(10, totalProcessed.get());
    }

    @Test
    void shouldNotReturnDuplicateEventsAcrossMultipleCalls() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            for (int i = 0; i < 3; i++) {
                outboxRepo.save(new OutboxEventJpaEntity(
                        "evt-" + i, "Transfer", String.valueOf(i), "TransferCompletedEvent",
                        "{}", LocalDateTime.now().plusSeconds(i), false, null));
            }
        });

        List<OutboxEventJpaEntity> firstBatch = tx.execute(status ->
                lockRepository.findAndLockUnprocessed(10));
        assertEquals(3, firstBatch.size());

        firstBatch.forEach(e -> {
            e.setProcessed(true);
            e.setProcessedAt(LocalDateTime.now());
            outboxRepo.save(e);
        });

        List<OutboxEventJpaEntity> secondBatch = tx.execute(status ->
                lockRepository.findAndLockUnprocessed(10));
        assertTrue(secondBatch.isEmpty(), "Same unprocessed events should not be returned again without processing");
    }

    @Test
    void shouldReturnRemainingEventsAfterProcessingSome() {
        for (int i = 0; i < 5; i++) {
            outboxRepo.save(new OutboxEventJpaEntity(
                    "evt-" + i, "Transfer", String.valueOf(i), "TransferCompletedEvent",
                    "{}", LocalDateTime.now().plusSeconds(i), false, null));
        }

        List<OutboxEventJpaEntity> allEvents = outboxRepo.findAll();
        OutboxEventJpaEntity toProcess = allEvents.get(0);
        toProcess.setProcessed(true);
        toProcess.setProcessedAt(LocalDateTime.now());
        outboxRepo.save(toProcess);

        List<OutboxEventJpaEntity> remaining = lockRepository.findAndLockUnprocessed(10);
        assertEquals(4, remaining.size());
        assertTrue(remaining.stream().noneMatch(e -> e.getId().equals(toProcess.getId())));
    }
}
