package com.bank.app.common.adapter.in.outbox;

import com.bank.app.common.adapter.in.config.OutboxProperties;
import com.bank.app.common.application.port.out.OutboxPort;
import com.bank.app.common.application.port.out.OutboxPort.EventEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class OutboxPollerTest {

    @Mock
    private OutboxPort outboxPort;

    @Mock
    private OutboxProcessor outboxProcessor;

    private OutboxPoller outboxPoller;

    private final OutboxProperties outboxProperties = new OutboxProperties(5, 50, 0, 2000);

    @BeforeEach
    void setUp() {
        outboxPoller = new OutboxPoller(outboxPort, outboxProcessor, outboxProperties);
    }

    private EventEntry event(String id) {
        return new EventEntry(id, "transfer", "agg-1", "TransferCompletedEvent",
                "{}", 0, false, false, null, 0, LocalDateTime.now());
    }

    @Test
    void shouldPollAndProcessEvents() {
        List<EventEntry> events = List.of(event("e1"), event("e2"));
        when(outboxPort.findAndLockUnprocessed(50, -1)).thenReturn(events);

        outboxPoller.pollAndProcessEvents();

        verify(outboxProcessor).processEvent(events.get(0));
        verify(outboxProcessor).processEvent(events.get(1));
    }

    @Test
    void shouldDoNothingWhenNoUnprocessedEvents() {
        when(outboxPort.findAndLockUnprocessed(50, -1)).thenReturn(List.of());

        outboxPoller.pollAndProcessEvents();

        verify(outboxProcessor, never()).processEvent(any());
    }

    @Test
    void shouldRecordFailureWhenProcessingFails() {
        EventEntry event = event("e-fail");
        when(outboxPort.findAndLockUnprocessed(50, -1)).thenReturn(List.of(event));
        doThrow(new RuntimeException("process failed")).when(outboxProcessor).processEvent(event);

        outboxPoller.pollAndProcessEvents();

        verify(outboxProcessor).recordFailure(eq(event), any(Throwable.class), eq(5));
    }

    @Test
    void shouldProcessEachPartitionWhenPartitionCountIsPositive() {
        outboxPoller = new OutboxPoller(outboxPort, outboxProcessor, new OutboxProperties(5, 50, 3, 2000));
        when(outboxPort.findAndLockUnprocessed(50, 0)).thenReturn(List.of(event("e0")));
        when(outboxPort.findAndLockUnprocessed(50, 1)).thenReturn(List.of(event("e1")));
        when(outboxPort.findAndLockUnprocessed(50, 2)).thenReturn(List.of(event("e2")));

        outboxPoller.pollAndProcessEvents();

        verify(outboxProcessor, times(3)).processEvent(any(EventEntry.class));
    }

    @Test
    void shouldSkipEmptyPartitions() {
        outboxPoller = new OutboxPoller(outboxPort, outboxProcessor, new OutboxProperties(5, 50, 2, 2000));
        when(outboxPort.findAndLockUnprocessed(50, 0)).thenReturn(List.of());
        when(outboxPort.findAndLockUnprocessed(50, 1)).thenReturn(List.of(event("e1")));

        outboxPoller.pollAndProcessEvents();

        verify(outboxProcessor, times(1)).processEvent(any(EventEntry.class));
    }

    @Test
    void shouldContinueProcessingRemainingEventsWhenOneFails() {
        EventEntry event1 = event("e1");
        EventEntry event2 = event("e2");
        when(outboxPort.findAndLockUnprocessed(50, -1)).thenReturn(List.of(event1, event2));
        doThrow(new RuntimeException("fail")).when(outboxProcessor).processEvent(event1);

        outboxPoller.pollAndProcessEvents();

        verify(outboxProcessor).processEvent(event1);
        verify(outboxProcessor).recordFailure(eq(event1), any(Throwable.class), eq(5));
        verify(outboxProcessor).processEvent(event2);
    }
}
