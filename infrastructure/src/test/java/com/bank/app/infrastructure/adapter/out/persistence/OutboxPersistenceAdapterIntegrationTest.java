package com.bank.app.infrastructure.adapter.out.persistence;

import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.common.TestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = TestApplication.class)
@Import(OutboxPersistenceAdapterIntegrationTest.TestConfig.class)
class OutboxPersistenceAdapterIntegrationTest extends AbstractSpringBootIntegrationTest {

    @Configuration
    static class TestConfig {
        @Bean
        UserDetailsService userDetailsService() {
            return new InMemoryUserDetailsManager();
        }
    }

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        outboxJpaRepository.deleteAll();
    }

    @Test
    @Transactional
    void shouldFindAndLockUnprocessedEvents() {
        outboxJpaRepository.save(new OutboxJpaEntity("evt-1", "Transfer", "1", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, 0, false, null, 0));

        List<OutboxJpaEntity> result = outboxJpaRepository.findAndLockUnprocessed(-1,
                org.springframework.data.domain.PageRequest.of(0, 10));

        assertEquals(1, result.size());
        assertEquals("evt-1", result.getFirst().getId());
    }

    @Test
    @Transactional
    void shouldExcludeProcessedEvents() {
        outboxJpaRepository.save(new OutboxJpaEntity("evt-1", "Transfer", "1", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), true, LocalDateTime.now(), 0, false, null, 0));
        outboxJpaRepository.save(new OutboxJpaEntity("evt-2", "Transfer", "2", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, 0, false, null, 0));

        List<OutboxJpaEntity> result = outboxJpaRepository.findAndLockUnprocessed(-1,
                org.springframework.data.domain.PageRequest.of(0, 10));

        assertEquals(1, result.size());
        assertEquals("evt-2", result.getFirst().getId());
    }

    @Test
    @Transactional
    void shouldExcludeDeadLetterEvents() {
        outboxJpaRepository.save(new OutboxJpaEntity("evt-1", "Transfer", "1", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, 5, true, "dead", 0));
        outboxJpaRepository.save(new OutboxJpaEntity("evt-2", "Transfer", "2", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, 0, false, null, 0));

        List<OutboxJpaEntity> result = outboxJpaRepository.findAndLockUnprocessed(-1,
                org.springframework.data.domain.PageRequest.of(0, 10));

        assertEquals(1, result.size());
        assertEquals("evt-2", result.getFirst().getId());
    }

    @Test
    @Transactional
    void shouldOrderByCreatedAtAscending() {
        outboxJpaRepository.save(new OutboxJpaEntity("evt-later", "Transfer", "1", "TransferCompletedEvent",
                "{}", LocalDateTime.now().plusMinutes(5), false, 0, false, null, 0));
        outboxJpaRepository.save(new OutboxJpaEntity("evt-earlier", "Transfer", "2", "TransferCompletedEvent",
                "{}", LocalDateTime.now().minusMinutes(5), false, 0, false, null, 0));

        List<OutboxJpaEntity> result = outboxJpaRepository.findAndLockUnprocessed(-1,
                org.springframework.data.domain.PageRequest.of(0, 10));

        assertEquals(2, result.size());
        assertEquals("evt-earlier", result.get(0).getId());
        assertEquals("evt-later", result.get(1).getId());
    }

    @Test
    @Transactional
    void shouldFilterByPartition() {
        outboxJpaRepository.save(new OutboxJpaEntity("evt-1", "Transfer", "1", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, 0, false, null, 0));
        outboxJpaRepository.save(new OutboxJpaEntity("evt-2", "Transfer", "2", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, 0, false, null, 1));

        List<OutboxJpaEntity> result = outboxJpaRepository.findAndLockUnprocessed(1,
                org.springframework.data.domain.PageRequest.of(0, 10));

        assertEquals(1, result.size());
        assertEquals("evt-2", result.getFirst().getId());
    }

    @Test
    @Transactional
    void shouldRespectLimit() {
        for (int i = 0; i < 5; i++) {
            outboxJpaRepository.save(new OutboxJpaEntity("evt-" + i, "Transfer", String.valueOf(i),
                    "TransferCompletedEvent", "{}", LocalDateTime.now().plusSeconds(i),
                    false, 0, false, null, 0));
        }

        List<OutboxJpaEntity> result = outboxJpaRepository.findAndLockUnprocessed(-1,
                org.springframework.data.domain.PageRequest.of(0, 3));

        assertEquals(3, result.size());
    }

    @Test
    void shouldHandleConcurrentSkipLockedAccess() throws InterruptedException {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            for (int i = 0; i < 10; i++) {
                outboxJpaRepository.save(new OutboxJpaEntity("evt-" + i, "Transfer", String.valueOf(i),
                        "TransferCompletedEvent", "{}", LocalDateTime.now().plusSeconds(i),
                        false, 0, false, null, 0));
            }
        });

        int threadCount = 3;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger totalProcessed = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            new Thread(() -> {
                List<OutboxJpaEntity> batch = tx.execute(status -> {
                    List<OutboxJpaEntity> locked = outboxJpaRepository.findAndLockUnprocessed(-1,
                            org.springframework.data.domain.PageRequest.of(0, 5));
                    for (OutboxJpaEntity e : locked) {
                        e.setProcessed(true);
                        e.setProcessedAt(LocalDateTime.now());
                        outboxJpaRepository.save(e);
                    }
                    return locked;
                });
                totalProcessed.addAndGet(batch != null ? batch.size() : 0);
                latch.countDown();
            }).start();
        }

        latch.await();
        assertEquals(10, totalProcessed.get(),
                "3 concurrent threads should collectively process 10 events without duplicates");
    }

    @Test
    void shouldNotReturnDuplicateEventsAcrossMultipleCalls() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            for (int i = 0; i < 3; i++) {
                outboxJpaRepository.save(new OutboxJpaEntity("evt-" + i, "Transfer", String.valueOf(i),
                        "TransferCompletedEvent", "{}", LocalDateTime.now().plusSeconds(i),
                        false, 0, false, null, 0));
            }
        });

        List<OutboxJpaEntity> firstBatch = tx.execute(status ->
                outboxJpaRepository.findAndLockUnprocessed(-1,
                        org.springframework.data.domain.PageRequest.of(0, 10)));
        assertEquals(3, firstBatch.size());

        for (OutboxJpaEntity e : firstBatch) {
            e.setProcessed(true);
            e.setProcessedAt(LocalDateTime.now());
            outboxJpaRepository.save(e);
        }

        List<OutboxJpaEntity> secondBatch = tx.execute(status ->
                outboxJpaRepository.findAndLockUnprocessed(-1,
                        org.springframework.data.domain.PageRequest.of(0, 10)));
        assertTrue(secondBatch.isEmpty(),
                "Processed events should not be returned");
    }

    @Test
    @Transactional
    void shouldReturnRemainingEventsAfterProcessingSome() {
        for (int i = 0; i < 5; i++) {
            outboxJpaRepository.save(new OutboxJpaEntity("evt-" + i, "Transfer", String.valueOf(i),
                    "TransferCompletedEvent", "{}", LocalDateTime.now().plusSeconds(i),
                    false, 0, false, null, 0));
        }

        List<OutboxJpaEntity> allEvents = outboxJpaRepository.findAll();
        OutboxJpaEntity toProcess = allEvents.getFirst();
        toProcess.setProcessed(true);
        toProcess.setProcessedAt(LocalDateTime.now());
        outboxJpaRepository.save(toProcess);

        List<OutboxJpaEntity> remaining = outboxJpaRepository.findAndLockUnprocessed(-1,
                org.springframework.data.domain.PageRequest.of(0, 10));

        assertEquals(4, remaining.size());
        assertTrue(remaining.stream().noneMatch(e -> e.getId().equals(toProcess.getId())));
    }
}
