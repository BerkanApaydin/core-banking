package com.bank.app.common.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPollerTest {

    @Mock
    private OutboxLockRepository lockRepository;

    @Mock
    private OutboxEventJpaRepository outboxRepo;

    @Mock
    private OutboxProcessor outboxProcessor;

    private OutboxPoller outboxPoller;

    @BeforeEach
    void setUp() {
        outboxPoller = new OutboxPoller(lockRepository, outboxRepo, outboxProcessor);
        ReflectionTestUtils.setField(outboxPoller, "maxRetries", 5);
        ReflectionTestUtils.setField(outboxPoller, "batchSize", 50);
        ReflectionTestUtils.setField(outboxPoller, "partitionCount", 0);
    }

    private OutboxEventJpaEntity event(String id) {
        return new OutboxEventJpaEntity(id, "transfer", "agg-1", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, null, 0, false, null, 0);
    }

    @Test
    void shouldPollAndProcessEvents() {
        List<OutboxEventJpaEntity> events = List.of(event("e1"), event("e2"));
        when(lockRepository.findAndLockUnprocessed(50, -1)).thenReturn(events);

        outboxPoller.pollAndProcessEvents();

        verify(outboxProcessor).processEvent(events.get(0));
        verify(outboxProcessor).processEvent(events.get(1));
    }

    @Test
    void shouldDoNothingWhenNoUnprocessedEvents() {
        when(lockRepository.findAndLockUnprocessed(50, -1)).thenReturn(List.of());

        outboxPoller.pollAndProcessEvents();

        verify(outboxProcessor, never()).processEvent(any());
    }

    @Test
    void shouldRecordFailureWhenProcessingFails() {
        OutboxEventJpaEntity event = event("e-fail");
        when(lockRepository.findAndLockUnprocessed(50, -1)).thenReturn(List.of(event));
        doThrow(new RuntimeException("process failed")).when(outboxProcessor).processEvent(event);

        outboxPoller.pollAndProcessEvents();

        verify(outboxProcessor).recordFailure(eq(event), any(Throwable.class), eq(5));
    }

    @Test
    void shouldProcessEachPartitionWhenPartitionCountIsPositive() {
        ReflectionTestUtils.setField(outboxPoller, "partitionCount", 3);
        when(lockRepository.findAndLockUnprocessed(50, 0)).thenReturn(List.of(event("e0")));
        when(lockRepository.findAndLockUnprocessed(50, 1)).thenReturn(List.of(event("e1")));
        when(lockRepository.findAndLockUnprocessed(50, 2)).thenReturn(List.of(event("e2")));

        outboxPoller.pollAndProcessEvents();

        verify(outboxProcessor, times(3)).processEvent(any(OutboxEventJpaEntity.class));
    }

    @Test
    void shouldSkipEmptyPartitions() {
        ReflectionTestUtils.setField(outboxPoller, "partitionCount", 2);
        when(lockRepository.findAndLockUnprocessed(50, 0)).thenReturn(List.of());
        when(lockRepository.findAndLockUnprocessed(50, 1)).thenReturn(List.of(event("e1")));

        outboxPoller.pollAndProcessEvents();

        verify(outboxProcessor, times(1)).processEvent(any(OutboxEventJpaEntity.class));
    }

    @Test
    void shouldContinueProcessingRemainingEventsWhenOneFails() {
        OutboxEventJpaEntity event1 = event("e1");
        OutboxEventJpaEntity event2 = event("e2");
        when(lockRepository.findAndLockUnprocessed(50, -1)).thenReturn(List.of(event1, event2));
        doThrow(new RuntimeException("fail")).when(outboxProcessor).processEvent(event1);

        outboxPoller.pollAndProcessEvents();

        verify(outboxProcessor).processEvent(event1);
        verify(outboxProcessor).recordFailure(eq(event1), any(Throwable.class), eq(5));
        verify(outboxProcessor).processEvent(event2);
    }
}
