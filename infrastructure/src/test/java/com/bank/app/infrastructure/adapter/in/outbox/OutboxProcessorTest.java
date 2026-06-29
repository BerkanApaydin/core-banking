package com.bank.app.infrastructure.adapter.in.outbox;

import com.bank.app.infrastructure.adapter.out.outbox.OutboxEventHandler;
import com.bank.app.common.application.port.out.OutboxPort;
import com.bank.app.common.application.port.out.OutboxPort.EventEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class OutboxProcessorTest {

    @Mock
    private OutboxPort outboxPort;

    @Mock
    private OutboxEventHandler handler;

    private OutboxProcessor outboxProcessor;

    @BeforeEach
    void setUp() {
        outboxProcessor = new OutboxProcessor(outboxPort, List.of(handler));
    }

    private EventEntry event(String id, String eventType) {
        return new EventEntry(id, "transfer", "agg-1", eventType,
                "{\"key\":\"value\"}", 0, false, false, null, 0, LocalDateTime.now());
    }

    @Test
    void shouldProcessEventSuccessfully() throws Exception {
        EventEntry event = event("evt-1", "TransferCompletedEvent");
        when(outboxPort.findByIdForUpdateSkipLocked("evt-1")).thenReturn(Optional.of(event));
        when(handler.supports("TransferCompletedEvent")).thenReturn(true);

        outboxProcessor.processEvent(event);

        verify(handler).handle(event);
        verify(outboxPort).markProcessed("evt-1");
    }

    @Test
    void shouldDoNothingWhenEventNotFound() {
        EventEntry fallback = event("evt-missing", "TransferCompletedEvent");
        when(outboxPort.findByIdForUpdateSkipLocked("evt-missing")).thenReturn(Optional.empty());

        outboxProcessor.processEvent(fallback);

        verifyNoInteractions(handler);
        verify(outboxPort, never()).markProcessed(any());
    }

    @Test
    void shouldThrowWhenNoHandlerFound() {
        EventEntry event = event("evt-2", "UnknownEventType");
        when(outboxPort.findByIdForUpdateSkipLocked("evt-2")).thenReturn(Optional.of(event));
        when(handler.supports("UnknownEventType")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> outboxProcessor.processEvent(event));
        assertTrue(ex.getCause() instanceof IllegalStateException);
        assertTrue(ex.getCause().getMessage().contains("UnknownEventType"));
        verify(outboxPort, never()).markProcessed(any());
    }

    @Test
    void shouldRethrowExceptionWhenHandlerFails() throws Exception {
        EventEntry event = event("evt-3", "TransferCompletedEvent");
        when(outboxPort.findByIdForUpdateSkipLocked("evt-3")).thenReturn(Optional.of(event));
        when(handler.supports("TransferCompletedEvent")).thenReturn(true);
        doThrow(new RuntimeException("Handler failed")).when(handler).handle(event);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> outboxProcessor.processEvent(event));
        assertEquals("Handler failed", ex.getCause().getMessage());
    }

    @Test
    void shouldRecordFailureAndMoveToDeadLetterAfterMaxRetries() {
        EventEntry event = new EventEntry("evt-4", "transfer", "agg-1", "TransferCompletedEvent",
                "{}", 4, false, false, null, 0, LocalDateTime.now());
        when(outboxPort.findByIdForUpdateSkipLocked("evt-4")).thenReturn(Optional.of(event));

        outboxProcessor.recordFailure(event, new RuntimeException("fail"), 5);

        verify(outboxPort).markDeadLetter("evt-4", "fail", 5);
    }

    @Test
    void shouldRecordFailureAndRetryWhenUnderMaxRetries() {
        EventEntry event = new EventEntry("evt-5", "transfer", "agg-1", "TransferCompletedEvent",
                "{}", 1, false, false, null, 0, LocalDateTime.now());
        when(outboxPort.findByIdForUpdateSkipLocked("evt-5")).thenReturn(Optional.of(event));

        outboxProcessor.recordFailure(event, new RuntimeException("transient"), 5);

        verify(outboxPort).markFailed("evt-5", "transient", 2);
    }

    @Test
    void shouldDoNothingOnRecordFailureWhenEventNotFound() {
        EventEntry fallback = event("evt-missing", "TransferCompletedEvent");
        when(outboxPort.findByIdForUpdateSkipLocked("evt-missing")).thenReturn(Optional.empty());

        outboxProcessor.recordFailure(fallback, new RuntimeException("fail"), 5);

        verify(outboxPort, never()).markProcessed(any());
        verify(outboxPort, never()).markFailed(any(), any(), anyInt());
        verify(outboxPort, never()).markDeadLetter(any(), any(), anyInt());
    }

    @Test
    void shouldTruncateErrorMessageWhenTooLong() {
        EventEntry event = new EventEntry("evt-6", "transfer", "agg-1", "TransferCompletedEvent",
                "{}", 0, false, false, null, 0, LocalDateTime.now());
        when(outboxPort.findByIdForUpdateSkipLocked("evt-6")).thenReturn(Optional.of(event));

        String longMsg = "a".repeat(3000);
        outboxProcessor.recordFailure(event, new RuntimeException(longMsg), 5);

        verify(outboxPort).markFailed("evt-6", longMsg.substring(0, 2000), 1);
    }

    @Test
    void shouldHandleNullErrorMessage() {
        EventEntry event = new EventEntry("evt-7", "transfer", "agg-1", "TransferCompletedEvent",
                "{}", 0, false, false, null, 0, LocalDateTime.now());
        when(outboxPort.findByIdForUpdateSkipLocked("evt-7")).thenReturn(Optional.of(event));

        outboxProcessor.recordFailure(event, new RuntimeException(), 5);

        verify(outboxPort).markFailed("evt-7", null, 1);
    }
}
