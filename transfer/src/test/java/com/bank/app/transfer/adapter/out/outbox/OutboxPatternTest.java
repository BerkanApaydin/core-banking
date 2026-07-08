package com.bank.app.transfer.adapter.out.outbox;

import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.infrastructure.adapter.in.config.OutboxProperties;
import com.bank.app.infrastructure.adapter.in.outbox.OutboxPoller;
import com.bank.app.infrastructure.adapter.in.outbox.OutboxProcessor;
import com.bank.app.common.application.port.out.OutboxEventPort;
import com.bank.app.common.application.port.out.OutboxPort;
import com.bank.app.common.application.port.out.OutboxPort.EventEntry;
import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import com.bank.app.transfer.domain.TransferCompletedEvent;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.transfer.domain.TransferStatus;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
class OutboxPatternTest {

    private OutboxPort outboxPort;
    private ObjectMapper objectMapper;
    private ApplicationEventPublisher eventPublisher;
    private IdempotencyPort idempotencyPort;

    private OutboxPoller outboxPoller;
    private TransferCompletedOutboxHandler handler;

    private final java.util.Map<String, EventEntry> entityMap = new java.util.concurrent.ConcurrentHashMap<>();

    private EventEntry register(EventEntry entry) {
        entityMap.put(entry.id(), entry);
        return entry;
    }

    private List<EventEntry> register(List<EventEntry> entries) {
        entries.forEach(this::register);
        return entries;
    }

    private void stubUnprocessed(List<EventEntry> entries) {
        register(entries);
        when(outboxPort.findAndLockUnprocessed(anyInt(), anyInt())).thenReturn(entries);
    }

    private void stubUnprocessed(int limit, int partition, List<EventEntry> entries) {
        register(entries);
        when(outboxPort.findAndLockUnprocessed(limit, partition)).thenReturn(entries);
    }

    private static EventEntry eventEntry(String id, String aggregateType, String aggregateId,
            String eventType, String payload, LocalDateTime createdAt,
            boolean processed, LocalDateTime processedAt,
            int retryCount, boolean deadLetter, String lastError) {
        return new EventEntry(id, aggregateType, aggregateId, eventType, payload,
                retryCount, processed, deadLetter, lastError, 0, createdAt);
    }

    private static EventEntry eventEntry(String id, String eventType, String payload) {
        return eventEntry(id, "Transfer", "123", eventType, payload,
                LocalDateTime.now(), false, null, 0, false, null);
    }

    private final OutboxProperties defaultOutboxProperties = new OutboxProperties(5, 50, 0, 2000);

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.findAndRegisterModules();
        return mapper;
    }

    @BeforeEach
    void setUp() {
        entityMap.clear();
        outboxPort = mock(OutboxPort.class);
        objectMapper = createMapper();
        eventPublisher = mock(ApplicationEventPublisher.class);
        idempotencyPort = mock(IdempotencyPort.class);

        when(outboxPort.findByIdForUpdateSkipLocked(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return Optional.ofNullable(entityMap.get(id));
        });

        when(idempotencyPort.tryCreate(anyString(), any())).thenReturn(true);

        handler = new TransferCompletedOutboxHandler(objectMapper, eventPublisher, idempotencyPort);
        OutboxProcessor processor = new OutboxProcessor(outboxPort, List.of(handler));
        outboxPoller = new OutboxPoller(outboxPort, processor, defaultOutboxProperties);
    }

    private static String transferCompletedJson(long transferId, long senderId, long receiverId,
            BigDecimal amount, String currency) throws Exception {
        ObjectMapper mapper = createMapper();
        return mapper.writeValueAsString(new TransferCompletedEvent(
                transferId, senderId, receiverId,
                Money.of(amount, Currency.valueOf(currency)),
                TransferStatus.COMPLETED, LocalDateTime.now()));
    }

    @Test
    void shouldPollAndProcessUnprocessedEvents() throws Exception {
        String jsonPayload = transferCompletedJson(123L, 1L, 2L, new BigDecimal("100.00"), "TRY");
        EventEntry entity = eventEntry("event-uuid", "TransferCompletedEvent", jsonPayload);
        stubUnprocessed(List.of(entity));

        outboxPoller.pollAndProcessEvents();

        ArgumentCaptor<AsyncTransferCompletedEvent> eventCaptor = ArgumentCaptor
                .forClass(AsyncTransferCompletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        AsyncTransferCompletedEvent asyncEvent = eventCaptor.getValue();
        assertEquals(123L, asyncEvent.transferId());
        assertEquals(1L, asyncEvent.senderAccountId());
        assertEquals(2L, asyncEvent.receiverAccountId());

        verify(outboxPort).markProcessed("event-uuid");
    }

    @Test
    void shouldIncrementRetryCountWhenProcessingFails() {
        EventEntry entity = eventEntry("event-uuid", "TransferCompletedEvent", "invalid json string");
        stubUnprocessed(List.of(entity));

        outboxPoller.pollAndProcessEvents();

        verifyNoInteractions(eventPublisher);
        verify(outboxPort).markFailed(eq("event-uuid"), anyString(), eq(1));
    }

    @Test
    void shouldMoveToDeadLetterAfterMaxRetries() {
        EventEntry entity = new EventEntry("event-uuid", "Transfer", "123", "TransferCompletedEvent",
                "invalid json string", 4, false, false, "previous error", 0, LocalDateTime.now());
        stubUnprocessed(List.of(entity));

        outboxPoller.pollAndProcessEvents();

        verify(outboxPort).markDeadLetter(eq("event-uuid"), anyString(), eq(5));
    }

    @Test
    void shouldSkipProcessingWhenNoUnprocessedEvents() {
        stubUnprocessed(List.of());

        outboxPoller.pollAndProcessEvents();

        verifyNoInteractions(eventPublisher);
        verify(outboxPort, never()).markProcessed(any());
        verify(outboxPort, never()).markFailed(any(), any(), anyInt());
    }

    @Test
    void shouldThrowWhenNoHandlerFoundForEventType() {
        EventEntry entity = eventEntry("event-uuid", "UnknownEventType", "{}");
        stubUnprocessed(List.of(entity));

        outboxPoller.pollAndProcessEvents();

        verify(outboxPort).markFailed(eq("event-uuid"), contains("No handler"), eq(1));
    }

    @Test
    void shouldSupportTransferCompletedEventType() {
        assertTrue(handler.supports("TransferCompletedEvent"));
        assertFalse(handler.supports("OtherEvent"));
    }

    @Test
    void shouldHandleTransferCompletedEventByPublishingAsyncEvent() throws Exception {
        String jsonPayload = transferCompletedJson(456L, 11L, 22L, new BigDecimal("250.50"), "USD");
        EventEntry entity = eventEntry("event-uuid-2", "TransferCompletedEvent", jsonPayload);

        handler.handle(entity);

        ArgumentCaptor<AsyncTransferCompletedEvent> captor = ArgumentCaptor.forClass(AsyncTransferCompletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        AsyncTransferCompletedEvent published = captor.getValue();
        assertEquals(456L, published.transferId());
        assertEquals(Currency.USD, published.amount().currency());
    }

    @Test
    void shouldUseMatchingHandlerAmongMultipleHandlers() throws Exception {
        OutboxEventPort matchingHandler = mock(OutboxEventPort.class);
        OutboxEventPort otherHandler = mock(OutboxEventPort.class);

        when(otherHandler.supports("TransferCompletedEvent")).thenReturn(false);
        when(matchingHandler.supports("TransferCompletedEvent")).thenReturn(true);

        OutboxPoller poller = new OutboxPoller(outboxPort,
                new OutboxProcessor(outboxPort, List.of(otherHandler, matchingHandler)),
                defaultOutboxProperties);

        EventEntry entity = eventEntry("id", "TransferCompletedEvent", "{}");
        stubUnprocessed(List.of(entity));

        poller.pollAndProcessEvents();

        verify(matchingHandler).handle(entity);
        verify(otherHandler, never()).handle(any());
    }

    @Test
    void shouldRetryWhenHandlerThrowsException() throws Exception {
        OutboxEventPort failingHandler = mock(OutboxEventPort.class);
        when(failingHandler.supports("TransferCompletedEvent")).thenReturn(true);
        doThrow(new RuntimeException("handler failed")).when(failingHandler).handle(any());

        OutboxPoller poller = new OutboxPoller(outboxPort,
                new OutboxProcessor(outboxPort, List.of(failingHandler)),
                defaultOutboxProperties);

        EventEntry entity = eventEntry("id", "TransferCompletedEvent", "{}");
        stubUnprocessed(List.of(entity));

        poller.pollAndProcessEvents();

        verify(outboxPort).markFailed("id", "handler failed", 1);
    }

    @Test
    void shouldMoveToDeadLetterExactlyAtMaxRetryBoundary() throws Exception {
        OutboxEventPort failingHandler = mock(OutboxEventPort.class);
        when(failingHandler.supports("TransferCompletedEvent")).thenReturn(true);
        doThrow(new RuntimeException("boom")).when(failingHandler).handle(any());

        OutboxPoller poller = new OutboxPoller(outboxPort,
                new OutboxProcessor(outboxPort, List.of(failingHandler)),
                defaultOutboxProperties);

        EventEntry entity = new EventEntry("id", "Transfer", "123", "TransferCompletedEvent",
                "{}", 4, false, false, null, 0, LocalDateTime.now());
        stubUnprocessed(List.of(entity));

        poller.pollAndProcessEvents();

        verify(outboxPort).markDeadLetter("id", "boom", 5);
    }

    @Test
    void shouldTruncateErrorMessageTo2000Characters() throws Exception {
        String longMessage = "x".repeat(2500);
        OutboxEventPort failingHandler = mock(OutboxEventPort.class);
        when(failingHandler.supports("TransferCompletedEvent")).thenReturn(true);
        doThrow(new RuntimeException(longMessage)).when(failingHandler).handle(any());

        OutboxPoller poller = new OutboxPoller(outboxPort,
                new OutboxProcessor(outboxPort, List.of(failingHandler)),
                defaultOutboxProperties);

        EventEntry entity = eventEntry("id", "TransferCompletedEvent", "{}");
        stubUnprocessed(List.of(entity));

        poller.pollAndProcessEvents();

        verify(outboxPort).markFailed("id", longMessage.substring(0, 2000), 1);
    }

    @Test
    void shouldHandleExceptionWithNullMessage() throws Exception {
        OutboxEventPort failingHandler = mock(OutboxEventPort.class);
        when(failingHandler.supports("TransferCompletedEvent")).thenReturn(true);
        doThrow(new RuntimeException((String) null)).when(failingHandler).handle(any());

        OutboxPoller poller = new OutboxPoller(outboxPort,
                new OutboxProcessor(outboxPort, List.of(failingHandler)),
                defaultOutboxProperties);

        EventEntry entity = eventEntry("id", "TransferCompletedEvent", "{}");
        stubUnprocessed(List.of(entity));

        poller.pollAndProcessEvents();

        verify(outboxPort).markFailed("id", null, 1);
    }

    @Test
    void shouldKeepShortErrorMessageWithoutTruncation() throws Exception {
        OutboxEventPort failingHandler = mock(OutboxEventPort.class);
        when(failingHandler.supports("TransferCompletedEvent")).thenReturn(true);
        doThrow(new RuntimeException("short message")).when(failingHandler).handle(any());

        OutboxPoller poller = new OutboxPoller(outboxPort,
                new OutboxProcessor(outboxPort, List.of(failingHandler)),
                defaultOutboxProperties);

        EventEntry entity = eventEntry("id", "TransferCompletedEvent", "{}");
        stubUnprocessed(List.of(entity));

        poller.pollAndProcessEvents();

        verify(outboxPort).markFailed("id", "short message", 1);
    }

    @Test
    void shouldHandleMultipleEvents() {
        EventEntry event1 = eventEntry("id-1", "TransferCompletedEvent", "{}");
        EventEntry event2 = eventEntry("id-2", "TransferCompletedEvent", "{}");

        OutboxEventPort handler1 = mock(OutboxEventPort.class);
        when(handler1.supports(anyString())).thenReturn(true);

        OutboxPoller poller = new OutboxPoller(outboxPort,
                new OutboxProcessor(outboxPort, List.of(handler1)),
                defaultOutboxProperties);

        stubUnprocessed(List.of(event1, event2));
        poller.pollAndProcessEvents();

        verify(outboxPort, times(2)).markProcessed(anyString());
    }

    @Test
    void shouldCreateWithCustomMaxRetries() throws Exception {
        OutboxEventPort failingHandler = mock(OutboxEventPort.class);
        when(failingHandler.supports("TransferCompletedEvent")).thenReturn(true);
        doThrow(new RuntimeException("fail")).when(failingHandler).handle(any());

        OutboxProcessor customProcessor = new OutboxProcessor(outboxPort, List.of(failingHandler));
        OutboxPoller pollerWithCustomRetries = new OutboxPoller(outboxPort, customProcessor,
                new OutboxProperties(3, 50, 0, 2000));

        EventEntry entity = new EventEntry("id", "Transfer", "123", "TransferCompletedEvent",
                "{}", 2, false, false, null, 0, LocalDateTime.now());
        stubUnprocessed(List.of(entity));

        pollerWithCustomRetries.pollAndProcessEvents();

        verify(outboxPort).markDeadLetter("id", "fail", 3);
    }

    @Test
    void shouldPollMultiplePartitionsWhenPartitionCountSet() throws Exception {
        OutboxProcessor partitionedProcessor = new OutboxProcessor(outboxPort, List.of(handler));
        OutboxPoller partitionedPoller = new OutboxPoller(outboxPort, partitionedProcessor,
                new OutboxProperties(5, 10, 3, 2000));

        String validPayload = transferCompletedJson(123L, 1L, 2L, new BigDecimal("100.00"), "TRY");
        EventEntry event = eventEntry("id", "TransferCompletedEvent", validPayload);

        stubUnprocessed(10, 0, List.of(event));
        stubUnprocessed(10, 1, List.of());
        stubUnprocessed(10, 2, List.of());

        partitionedPoller.pollAndProcessEvents();

        verify(outboxPort).markProcessed("id");
    }

    @Test
    void shouldHandleNoHandlersList() {
        OutboxProcessor emptyProcessor = new OutboxProcessor(outboxPort, List.of());
        OutboxPoller emptyPoller = new OutboxPoller(outboxPort, emptyProcessor, defaultOutboxProperties);

        EventEntry entity = eventEntry("id", "TransferCompletedEvent", "{}");
        stubUnprocessed(List.of(entity));

        emptyPoller.pollAndProcessEvents();

        verify(outboxPort).markFailed(eq("id"), contains("No handler"), eq(1));
    }
}
