package com.bank.app.transfer.infrastructure.outbox;

import com.bank.app.common.outbox.*;
import com.bank.app.transfer.AbstractIntegrationTest;
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

import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

@Import(OutboxLockRepository.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxLockRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxLockRepository lockRepository;

    @Autowired
    private OutboxEventJpaRepository outboxRepo;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        outboxRepo.deleteAll();
    }

    @AfterEach
    void tearDown() {
        outboxRepo.deleteAll();
    }

    @Test
    @Transactional
    void shouldReturnEmptyListWhenNoUnprocessedEvents() {
        List<OutboxEventJpaEntity> result = lockRepository.findAndLockUnprocessed(10, -1);
        assertTrue(result.isEmpty());
    }

    @Test
    @Transactional
    void shouldFindAndLockUnprocessedEventsWithSkipLocked() {
        outboxRepo.save(new OutboxEventJpaEntity(
                "evt-1", "Transfer", "1", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, null));

        List<OutboxEventJpaEntity> result = lockRepository.findAndLockUnprocessed(10, -1);
        assertEquals(1, result.size());
        assertEquals("evt-1", result.get(0).getId());
    }

    @Test
    @Transactional
    void shouldExcludeProcessedEvents() {
        outboxRepo.save(new OutboxEventJpaEntity(
                "evt-1", "Transfer", "1", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), true, LocalDateTime.now()));
        outboxRepo.save(new OutboxEventJpaEntity(
                "evt-2", "Transfer", "2", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, null));

        List<OutboxEventJpaEntity> result = lockRepository.findAndLockUnprocessed(10, -1);
        assertEquals(1, result.size());
        assertEquals("evt-2", result.get(0).getId());
    }

    @Test
    @Transactional
    void shouldExcludeDeadLetterEvents() {
        outboxRepo.save(new OutboxEventJpaEntity(
                "evt-1", "Transfer", "1", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, null,
                5, true, "dead"));
        outboxRepo.save(new OutboxEventJpaEntity(
                "evt-2", "Transfer", "2", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, null));

        List<OutboxEventJpaEntity> result = lockRepository.findAndLockUnprocessed(10, -1);
        assertEquals(1, result.size());
        assertEquals("evt-2", result.get(0).getId());
    }

    @Test
    @Transactional
    void shouldOrderByCreatedAtAscending() {
        outboxRepo.save(new OutboxEventJpaEntity(
                "evt-later", "Transfer", "1", "TransferCompletedEvent",
                "{}", LocalDateTime.now().plusMinutes(5), false, null));
        outboxRepo.save(new OutboxEventJpaEntity(
                "evt-earlier", "Transfer", "2", "TransferCompletedEvent",
                "{}", LocalDateTime.now().minusMinutes(5), false, null));

        List<OutboxEventJpaEntity> result = lockRepository.findAndLockUnprocessed(10, -1);
        assertEquals(2, result.size());
        assertEquals("evt-earlier", result.get(0).getId());
        assertEquals("evt-later", result.get(1).getId());
    }

    @Test
    @Transactional
    void shouldRespectLimit() {
        for (int i = 0; i < 5; i++) {
            outboxRepo.save(new OutboxEventJpaEntity(
                    "evt-" + i, "Transfer", String.valueOf(i), "TransferCompletedEvent",
                    "{}", LocalDateTime.now().plusSeconds(i), false, null));
        }

        List<OutboxEventJpaEntity> result = lockRepository.findAndLockUnprocessed(3, -1);
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
                    List<OutboxEventJpaEntity> locked = lockRepository.findAndLockUnprocessed(5, -1);
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
                lockRepository.findAndLockUnprocessed(10, -1));
        assertEquals(3, firstBatch.size());

        firstBatch.forEach(e -> {
            e.setProcessed(true);
            e.setProcessedAt(LocalDateTime.now());
            outboxRepo.save(e);
        });

        List<OutboxEventJpaEntity> secondBatch = tx.execute(status ->
                lockRepository.findAndLockUnprocessed(10, -1));
        assertTrue(secondBatch.isEmpty(), "Same unprocessed events should not be returned again without processing");
    }

    @Test
    @Transactional
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

        List<OutboxEventJpaEntity> remaining = lockRepository.findAndLockUnprocessed(10, -1);
        assertEquals(4, remaining.size());
        assertTrue(remaining.stream().noneMatch(e -> e.getId().equals(toProcess.getId())));
    }
}
