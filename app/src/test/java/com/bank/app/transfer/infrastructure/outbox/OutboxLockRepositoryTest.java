package com.bank.app.transfer.infrastructure.outbox;

import com.bank.app.common.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Import(OutboxLockRepository.class)
@SuppressWarnings("null")
class OutboxLockRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxLockRepository repository;

    @Autowired
    private OutboxEventJpaRepository outboxRepo;

    @Test
    void shouldReturnEmptyListWhenNoUnprocessedEvents() {
        List<OutboxEventJpaEntity> result = repository.findAndLockUnprocessed(10, -1);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnUnprocessedEventsWithSkipLocked() {
        OutboxEventJpaEntity event = new OutboxEventJpaEntity(
                "evt-1", "Transfer", "1", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, null);
        outboxRepo.save(event);

        List<OutboxEventJpaEntity> result = repository.findAndLockUnprocessed(10, -1);
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

        List<OutboxEventJpaEntity> result = repository.findAndLockUnprocessed(10, -1);
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

        List<OutboxEventJpaEntity> result = repository.findAndLockUnprocessed(10, -1);
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

        List<OutboxEventJpaEntity> result = repository.findAndLockUnprocessed(10, -1);
        assertEquals(2, result.size());
        assertEquals("evt-earlier", result.get(0).getId());
        assertEquals("evt-later", result.get(1).getId());
    }

    @Test
    void shouldFilterByPartitionWithSkipLocked() {
        outboxRepo.save(new OutboxEventJpaEntity(
                "evt-1", "Transfer", "1", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, null, 0, false, null, 0));
        outboxRepo.save(new OutboxEventJpaEntity(
                "evt-2", "Transfer", "2", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, null, 0, false, null, 1));

        List<OutboxEventJpaEntity> result = repository.findAndLockUnprocessed(10, 1);
        assertEquals(1, result.size());
        assertEquals("evt-2", result.get(0).getId());
    }

    @Test
    void shouldRespectLimit() {
        for (int i = 0; i < 5; i++) {
            outboxRepo.save(new OutboxEventJpaEntity(
                    "evt-" + i, "Transfer", String.valueOf(i), "TransferCompletedEvent",
                    "{}", LocalDateTime.now().plusSeconds(i), false, null));
        }

        List<OutboxEventJpaEntity> result = repository.findAndLockUnprocessed(3, -1);
        assertEquals(3, result.size());
    }
}
