package com.bank.app.transfer.adapter.out.outbox;

import com.bank.app.infrastructure.adapter.out.outbox.OutboxLockRepository;
import com.bank.app.infrastructure.adapter.out.persistence.OutboxJpaEntity;
import com.bank.app.infrastructure.adapter.out.persistence.OutboxJpaRepository;
import com.bank.app.transfer.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Import(OutboxLockRepository.class)
@TestPropertySource(properties = {
        "app.outbox.use-skip-locked=false"
})
@SuppressWarnings("null")
class OutboxLockRepositoryNonSkipLockedIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxLockRepository repository;

    @Autowired
    private OutboxJpaRepository outboxRepo;

    @Test
    void shouldReturnEmptyListWhenNoUnprocessedEvents() {
        List<OutboxJpaEntity> result = repository.findAndLockUnprocessed(10, -1);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnUnprocessedEventsWithoutSkipLocked() {
        outboxRepo.save(new OutboxJpaEntity(
                "evt-1", "Transfer", "1", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, 0, false, null, 0));

        List<OutboxJpaEntity> result = repository.findAndLockUnprocessed(10, -1);
        assertEquals(1, result.size());
        assertEquals("evt-1", result.get(0).getId());
    }

    @Test
    void shouldExcludeProcessedEvents() {
        outboxRepo.save(new OutboxJpaEntity(
                "evt-1", "Transfer", "1", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), true, LocalDateTime.now(), 0, false, null, 0));
        outboxRepo.save(new OutboxJpaEntity(
                "evt-2", "Transfer", "2", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, 0, false, null, 0));

        List<OutboxJpaEntity> result = repository.findAndLockUnprocessed(10, -1);
        assertEquals(1, result.size());
        assertEquals("evt-2", result.get(0).getId());
    }

    @Test
    void shouldExcludeDeadLetterEvents() {
        outboxRepo.save(new OutboxJpaEntity(
                "evt-1", "Transfer", "1", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, 5, true, "dead", 0));
        outboxRepo.save(new OutboxJpaEntity(
                "evt-2", "Transfer", "2", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, 0, false, null, 0));

        List<OutboxJpaEntity> result = repository.findAndLockUnprocessed(10, -1);
        assertEquals(1, result.size());
        assertEquals("evt-2", result.get(0).getId());
    }

    @Test
    void shouldOrderByCreatedAtAscending() {
        outboxRepo.save(new OutboxJpaEntity(
                "evt-later", "Transfer", "1", "TransferCompletedEvent",
                "{}", LocalDateTime.now().plusMinutes(5), false, 0, false, null, 0));
        outboxRepo.save(new OutboxJpaEntity(
                "evt-earlier", "Transfer", "2", "TransferCompletedEvent",
                "{}", LocalDateTime.now().minusMinutes(5), false, 0, false, null, 0));

        List<OutboxJpaEntity> result = repository.findAndLockUnprocessed(10, -1);
        assertEquals(2, result.size());
        assertEquals("evt-earlier", result.get(0).getId());
        assertEquals("evt-later", result.get(1).getId());
    }

    @Test
    void shouldFilterByPartitionWithoutSkipLocked() {
        outboxRepo.save(new OutboxJpaEntity(
                "evt-1", "Transfer", "1", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, 0, false, null, 0));
        outboxRepo.save(new OutboxJpaEntity(
                "evt-2", "Transfer", "2", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, 0, false, null, 1));

        List<OutboxJpaEntity> result = repository.findAndLockUnprocessed(10, 1);
        assertEquals(1, result.size());
        assertEquals("evt-2", result.get(0).getId());
    }

    @Test
    void shouldRespectLimit() {
        for (int i = 0; i < 5; i++) {
            outboxRepo.save(new OutboxJpaEntity(
                    "evt-" + i, "Transfer", String.valueOf(i), "TransferCompletedEvent",
                    "{}", LocalDateTime.now().plusSeconds(i), false, 0, false, null, 0));
        }

        List<OutboxJpaEntity> result = repository.findAndLockUnprocessed(3, -1);
        assertEquals(3, result.size());
    }
}
