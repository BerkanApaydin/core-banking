package com.bank.app.transfer.infrastructure.outbox;

import com.bank.app.common.outbox.*;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferCompletedEvent;
import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.transfer.domain.TransferStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
class OutboxPatternTest {

    private OutboxEventJpaRepository outboxRepo;
    private OutboxLockRepository lockRepository;
    private ObjectMapper objectMapper;
    private ApplicationEventPublisher eventPublisher;

    private OutboxEventListener eventListener;
    private OutboxPoller outboxPoller;
    private TransferCompletedOutboxHandler handler;

    private final java.util.Map<String, OutboxEventJpaEntity> entityMap = new java.util.concurrent.ConcurrentHashMap<>();

    private OutboxEventJpaEntity register(OutboxEventJpaEntity entity) {
        entityMap.put(entity.getId(), entity);
        return entity;
    }

    private List<OutboxEventJpaEntity> register(List<OutboxEventJpaEntity> entities) {
        entities.forEach(this::register);
        return entities;
    }

    private void stubUnprocessed(List<OutboxEventJpaEntity> entities) {
        register(entities);
        when(lockRepository.findAndLockUnprocessed(anyInt(), anyInt())).thenReturn(entities);
    }

    private void stubUnprocessed(int limit, int partition, List<OutboxEventJpaEntity> entities) {
        register(entities);
        when(lockRepository.findAndLockUnprocessed(limit, partition)).thenReturn(entities);
    }

    @BeforeEach
    void setUp() {
        entityMap.clear();
        outboxRepo = mock(OutboxEventJpaRepository.class);
        lockRepository = mock(OutboxLockRepository.class);
        objectMapper = new ObjectMapper();
        eventPublisher = mock(ApplicationEventPublisher.class);

        when(outboxRepo.findByIdForUpdateSkipLocked(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return java.util.Optional.ofNullable(entityMap.get(id));
        });
        when(outboxRepo.findById(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return java.util.Optional.ofNullable(entityMap.get(id));
        });

        // Also stub save to return the argument by default and update the map
        when(outboxRepo.save(any(OutboxEventJpaEntity.class))).thenAnswer(invocation -> {
            OutboxEventJpaEntity entity = invocation.getArgument(0);
            if (entity.getId() != null) {
                entityMap.put(entity.getId(), entity);
            }
            return entity;
        });

        eventListener = new OutboxEventListener(outboxRepo, objectMapper);
        handler = new TransferCompletedOutboxHandler(objectMapper, eventPublisher);
        OutboxProcessor processor = new OutboxProcessor(outboxRepo, List.of(handler));
        outboxPoller = new OutboxPoller(lockRepository, outboxRepo, processor);
        ReflectionTestUtils.setField(outboxPoller, "maxRetries", 5);
    }

    @Test
    void shouldSaveOutboxEventWhenTransferCompletedEventFired() throws Exception {
        Transfer transfer = new Transfer(
                123L, 1L, 2L,
                new Money(new BigDecimal("100.00"), Currency.TRY),
                TransferStatus.COMPLETED,
                LocalDateTime.now());
        TransferCompletedEvent event = new TransferCompletedEvent(
                transfer.getId(), transfer.getSenderAccountId(),
                transfer.getReceiverAccountId(), transfer.getAmount(),
                transfer.getStatus());

        OutboxEventJpaEntity[] savedEventHolder = new OutboxEventJpaEntity[1];
        when(outboxRepo.save(any(OutboxEventJpaEntity.class))).thenAnswer(invocation -> {
            savedEventHolder[0] = invocation.getArgument(0);
            return savedEventHolder[0];
        });

        eventListener.handleTransferCompleted(event);

        verify(outboxRepo).save(any(OutboxEventJpaEntity.class));

        OutboxEventJpaEntity saved = savedEventHolder[0];
        assertNotNull(saved);
        assertEquals("Transfer", saved.getAggregateType());
        assertEquals("123", saved.getAggregateId());
        assertEquals("TransferCompletedEvent", saved.getEventType());
        assertFalse(saved.isProcessed());
        assertNotNull(saved.getPayload());
        assertEquals(0, saved.getPartition());
    }

    @Test
    void shouldPollAndProcessUnprocessedEvents() throws Exception {
        OutboxEventListener.TransferEventPayload payload = new OutboxEventListener.TransferEventPayload(
                123L, 1L, 2L, new BigDecimal("100.00"), "TRY");
        String jsonPayload = objectMapper.writeValueAsString(payload);

        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                "event-uuid", "Transfer", "123", "TransferCompletedEvent",
                jsonPayload, LocalDateTime.now(), false, null);

        stubUnprocessed(List.of(entity));

        outboxPoller.pollAndProcessEvents();

        ArgumentCaptor<AsyncTransferCompletedEvent> eventCaptor = ArgumentCaptor
                .forClass(AsyncTransferCompletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        AsyncTransferCompletedEvent asyncEvent = eventCaptor.getValue();
        assertEquals(123L, asyncEvent.transferId());
        assertEquals(1L, asyncEvent.senderAccountId());
        assertEquals(2L, asyncEvent.receiverAccountId());

        assertTrue(entity.isProcessed());
        assertNotNull(entity.getProcessedAt());
        verify(outboxRepo).save(entity);
    }

    @Test
    void shouldIncrementRetryCountWhenProcessingFails() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                "event-uuid", "Transfer", "123", "TransferCompletedEvent",
                "invalid json string", LocalDateTime.now(), false, null);

        stubUnprocessed(List.of(entity));

        outboxPoller.pollAndProcessEvents();

        verifyNoInteractions(eventPublisher);
        assertFalse(entity.isProcessed());
        assertEquals(1, entity.getRetryCount());
        assertNotNull(entity.getLastError());
        verify(outboxRepo).save(entity);
    }

    @Test
    void shouldMoveToDeadLetterAfterMaxRetries() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                "event-uuid", "Transfer", "123", "TransferCompletedEvent",
                "invalid json string", LocalDateTime.now(), false, null,
                4, false, "previous error");

        stubUnprocessed(List.of(entity));

        outboxPoller.pollAndProcessEvents();

        assertTrue(entity.isDeadLetter());
        assertEquals(5, entity.getRetryCount());
        verify(outboxRepo).save(entity);
    }

    @Test
    void shouldSkipProcessingWhenNoUnprocessedEvents() {
        stubUnprocessed(List.of());

        outboxPoller.pollAndProcessEvents();

        verifyNoInteractions(eventPublisher);
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void shouldThrowWhenNoHandlerFoundForEventType() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                "event-uuid", "Transfer", "123", "UnknownEventType",
                "{}", LocalDateTime.now(), false, null);

        stubUnprocessed(List.of(entity));

        outboxPoller.pollAndProcessEvents();

        assertFalse(entity.isProcessed());
        assertEquals(1, entity.getRetryCount());
        assertNotNull(entity.getLastError());
        assertFalse(entity.isDeadLetter());
        verify(outboxRepo).save(entity);
    }

    @Test
    void shouldSupportTransferCompletedEventType() {
        assertTrue(handler.supports("TransferCompletedEvent"));
        assertFalse(handler.supports("OtherEvent"));
    }

    @Test
    void shouldHandleTransferCompletedEventByPublishingAsyncEvent() throws Exception {
        OutboxEventListener.TransferEventPayload payload = new OutboxEventListener.TransferEventPayload(
                456L, 11L, 22L, new BigDecimal("250.50"), "USD");
        String jsonPayload = objectMapper.writeValueAsString(payload);

        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                "event-uuid-2", "Transfer", "456", "TransferCompletedEvent",
                jsonPayload, LocalDateTime.now(), false, null);

        handler.handle(entity);

        ArgumentCaptor<AsyncTransferCompletedEvent> captor = ArgumentCaptor.forClass(AsyncTransferCompletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        AsyncTransferCompletedEvent published = captor.getValue();
        assertEquals(456L, published.transferId());
        assertEquals(Currency.USD, published.amount().currency());
    }

    @Test
    void shouldAssignPartitionWhenPartitionCountGreaterThanZero() throws Exception {
        ReflectionTestUtils.setField(eventListener, "partitionCount", 3);

        Transfer transfer = new Transfer(
                5L, 1L, 2L,
                new Money(new BigDecimal("100.00"), Currency.TRY),
                TransferStatus.COMPLETED,
                LocalDateTime.now());
        TransferCompletedEvent event = new TransferCompletedEvent(
                transfer.getId(), transfer.getSenderAccountId(),
                transfer.getReceiverAccountId(), transfer.getAmount(),
                transfer.getStatus());

        OutboxEventJpaEntity[] savedEventHolder = new OutboxEventJpaEntity[1];
        when(outboxRepo.save(any(OutboxEventJpaEntity.class))).thenAnswer(invocation -> {
            savedEventHolder[0] = invocation.getArgument(0);
            return savedEventHolder[0];
        });

        eventListener.handleTransferCompleted(event);

        int expectedPartition = Math.floorMod(5L, 3);
        assertEquals(expectedPartition, savedEventHolder[0].getPartition());
    }

    @Test
    void shouldWrapListenerExceptionAsRuntimeException() throws Exception {
        Transfer transfer = new Transfer(
                999L, 1L, 2L,
                new Money(new BigDecimal("100.00"), Currency.TRY),
                TransferStatus.COMPLETED,
                LocalDateTime.now());
        TransferCompletedEvent event = new TransferCompletedEvent(
                transfer.getId(), transfer.getSenderAccountId(),
                transfer.getReceiverAccountId(), transfer.getAmount(),
                transfer.getStatus());

        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new RuntimeException("boom"));
        OutboxEventListener failingListener = new OutboxEventListener(outboxRepo, failingMapper);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> failingListener.handleTransferCompleted(event));
        assertTrue(ex.getMessage().contains("Failed to save outbox event"));
        verifyNoInteractions(outboxRepo);
    }

    @Test
    void shouldUseEmptyListAndSaveEventWhenHandlerSucceeds() throws Exception {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                "event-uuid", "Transfer", "123", "TransferCompletedEvent",
                "{\"transferId\":1,\"senderAccountId\":1,\"receiverAccountId\":2,\"amount\":10,\"currency\":\"TRY\"}",
                LocalDateTime.now(), false, null);

        stubUnprocessed(List.of(entity));

        outboxPoller.pollAndProcessEvents();

        assertTrue(entity.isProcessed());
        assertNotNull(entity.getProcessedAt());
        assertEquals(0, entity.getRetryCount());
        assertNull(entity.getLastError());
        assertFalse(entity.isDeadLetter());
        verify(outboxRepo).save(entity);
    }

    @Test
    void shouldClearPreviousErrorWhenEventProcessedSuccessfully() throws Exception {
        OutboxEventListener.TransferEventPayload payload = new OutboxEventListener.TransferEventPayload(
                123L, 1L, 2L,
                new BigDecimal("100.00"), "TRY");

        String json = objectMapper.writeValueAsString(payload);

        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                "id",
                "Transfer",
                "123",
                "TransferCompletedEvent",
                json,
                LocalDateTime.now(),
                false,
                null,
                2,
                false,
                "old error");

        stubUnprocessed(List.of(entity));

        outboxPoller.pollAndProcessEvents();

        assertTrue(entity.isProcessed());
        assertNull(entity.getLastError());
    }

    @Test
    void shouldUseMatchingHandlerAmongMultipleHandlers() throws Exception {
        OutboxEventHandler matchingHandler = mock(OutboxEventHandler.class);
        OutboxEventHandler otherHandler = mock(OutboxEventHandler.class);

        when(otherHandler.supports("TransferCompletedEvent"))
                .thenReturn(false);

        when(matchingHandler.supports("TransferCompletedEvent"))
                .thenReturn(true);

        OutboxPoller poller = new OutboxPoller(
                lockRepository,
                outboxRepo,
                new OutboxProcessor(outboxRepo, List.of(otherHandler, matchingHandler)));

        ReflectionTestUtils.setField(poller, "maxRetries", 5);

        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                "id",
                "Transfer",
                "123",
                "TransferCompletedEvent",
                "{}",
                LocalDateTime.now(),
                false,
                null);

        stubUnprocessed(List.of(entity));

        poller.pollAndProcessEvents();

        verify(matchingHandler).handle(entity);
        verify(otherHandler, never()).handle(any());
    }

    @Test
    void shouldRetryWhenHandlerThrowsException() throws Exception {
        OutboxEventHandler failingHandler = mock(OutboxEventHandler.class);

        when(failingHandler.supports("TransferCompletedEvent"))
                .thenReturn(true);

        doThrow(new RuntimeException("handler failed"))
                .when(failingHandler)
                .handle(any());

        OutboxPoller poller = new OutboxPoller(
                lockRepository,
                outboxRepo,
                new OutboxProcessor(outboxRepo, List.of(failingHandler)));

        ReflectionTestUtils.setField(poller, "maxRetries", 5);

        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                "id",
                "Transfer",
                "123",
                "TransferCompletedEvent",
                "{}",
                LocalDateTime.now(),
                false,
                null);

        stubUnprocessed(List.of(entity));

        poller.pollAndProcessEvents();

        assertEquals(1, entity.getRetryCount());
        assertEquals("handler failed", entity.getLastError());
        assertFalse(entity.isDeadLetter());

        verify(outboxRepo).save(entity);
    }

    @Test
    void shouldMoveToDeadLetterExactlyAtMaxRetryBoundary() throws Exception {
        OutboxEventHandler failingHandler = mock(OutboxEventHandler.class);

        when(failingHandler.supports("TransferCompletedEvent"))
                .thenReturn(true);

        doThrow(new RuntimeException("boom"))
                .when(failingHandler)
                .handle(any());

        OutboxPoller poller = new OutboxPoller(
                lockRepository,
                outboxRepo,
                new OutboxProcessor(outboxRepo, List.of(failingHandler)));

        ReflectionTestUtils.setField(poller, "maxRetries", 5);

        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                "id",
                "Transfer",
                "123",
                "TransferCompletedEvent",
                "{}",
                LocalDateTime.now(),
                false,
                null,
                4,
                false,
                null);

        stubUnprocessed(List.of(entity));

        poller.pollAndProcessEvents();

        assertEquals(5, entity.getRetryCount());
        assertTrue(entity.isDeadLetter());
    }

    @Test
    void shouldTruncateErrorMessageTo2000Characters() throws Exception {
        String longMessage = "x".repeat(2500);

        OutboxEventHandler failingHandler = mock(OutboxEventHandler.class);

        when(failingHandler.supports("TransferCompletedEvent"))
                .thenReturn(true);

        doThrow(new RuntimeException(longMessage))
                .when(failingHandler)
                .handle(any());

        OutboxPoller poller = new OutboxPoller(
                lockRepository,
                outboxRepo,
                new OutboxProcessor(outboxRepo, List.of(failingHandler)));

        ReflectionTestUtils.setField(poller, "maxRetries", 5);

        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                "id",
                "Transfer",
                "123",
                "TransferCompletedEvent",
                "{}",
                LocalDateTime.now(),
                false,
                null);

        stubUnprocessed(List.of(entity));

        poller.pollAndProcessEvents();

        assertNotNull(entity.getLastError());
        assertEquals(2000, entity.getLastError().length());
    }

    @Test
    void shouldHandleExceptionWithNullMessage() throws Exception {

        OutboxEventHandler failingHandler = mock(OutboxEventHandler.class);

        when(failingHandler.supports("TransferCompletedEvent"))
                .thenReturn(true);

        doThrow(new RuntimeException((String) null))
                .when(failingHandler)
                .handle(any());

        OutboxPoller poller = new OutboxPoller(
                lockRepository,
                outboxRepo,
                new OutboxProcessor(outboxRepo, List.of(failingHandler)));

        ReflectionTestUtils.setField(poller, "maxRetries", 5);

        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                "id",
                "Transfer",
                "123",
                "TransferCompletedEvent",
                "{}",
                LocalDateTime.now(),
                false,
                null);

        stubUnprocessed(List.of(entity));

        poller.pollAndProcessEvents();

        assertNull(entity.getLastError());
    }

    @Test
    void shouldKeepShortErrorMessageWithoutTruncation() throws Exception {

        OutboxEventHandler failingHandler = mock(OutboxEventHandler.class);

        when(failingHandler.supports("TransferCompletedEvent"))
                .thenReturn(true);

        doThrow(new RuntimeException("short message"))
                .when(failingHandler)
                .handle(any());

        OutboxPoller poller = new OutboxPoller(
                lockRepository,
                outboxRepo,
                new OutboxProcessor(outboxRepo, List.of(failingHandler)));

        ReflectionTestUtils.setField(poller, "maxRetries", 5);

        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                "id",
                "Transfer",
                "123",
                "TransferCompletedEvent",
                "{}",
                LocalDateTime.now(),
                false,
                null);

        stubUnprocessed(List.of(entity));

        poller.pollAndProcessEvents();

        assertEquals("short message", entity.getLastError());
    }

    @Test
    void shouldHandleMultipleEvents() {
        OutboxEventJpaEntity event1 = new OutboxEventJpaEntity(
                "id-1", "Transfer", "123", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, null);
        OutboxEventJpaEntity event2 = new OutboxEventJpaEntity(
                "id-2", "Transfer", "456", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, null);

        OutboxEventHandler handler1 = mock(OutboxEventHandler.class);
        when(handler1.supports(anyString())).thenReturn(true);

        OutboxPoller poller = new OutboxPoller(lockRepository, outboxRepo, new OutboxProcessor(outboxRepo, List.of(handler1)));
        ReflectionTestUtils.setField(poller, "maxRetries", 5);

        stubUnprocessed(List.of(event1, event2));

        poller.pollAndProcessEvents();

        verify(outboxRepo, times(2)).save(any(OutboxEventJpaEntity.class));
    }

    @Test
    void shouldCreateWithCustomMaxRetries() {
        OutboxProcessor customProcessor = new OutboxProcessor(outboxRepo, List.of(handler));
        OutboxPoller pollerWithCustomRetries = new OutboxPoller(lockRepository, outboxRepo, customProcessor);
        ReflectionTestUtils.setField(pollerWithCustomRetries, "maxRetries", 3);
        ReflectionTestUtils.setField(pollerWithCustomRetries, "batchSize", 50);
        ReflectionTestUtils.setField(pollerWithCustomRetries, "partitionCount", 0);

        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                "id", "Transfer", "123", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, null,
                2, false, null);

        stubUnprocessed(List.of(entity));

        pollerWithCustomRetries.pollAndProcessEvents();

        assertEquals(3, entity.getRetryCount());
        assertTrue(entity.isDeadLetter());
    }

    @Test
    void shouldPollMultiplePartitionsWhenPartitionCountSet() throws Exception {
        OutboxProcessor partitionedProcessor = new OutboxProcessor(outboxRepo, List.of(handler));
        OutboxPoller partitionedPoller = new OutboxPoller(lockRepository, outboxRepo, partitionedProcessor);
        ReflectionTestUtils.setField(partitionedPoller, "maxRetries", 5);
        ReflectionTestUtils.setField(partitionedPoller, "batchSize", 10);
        ReflectionTestUtils.setField(partitionedPoller, "partitionCount", 3);

        String validPayload = "{\"transferId\":123,\"senderAccountId\":1,\"receiverAccountId\":2,\"amount\":100.00,\"currency\":\"TRY\"}";
        OutboxEventJpaEntity event = new OutboxEventJpaEntity(
                "id", "Transfer", "123", "TransferCompletedEvent",
                validPayload, LocalDateTime.now(), false, null);

        stubUnprocessed(10, 0, List.of(event));
        stubUnprocessed(10, 1, List.of());
        stubUnprocessed(10, 2, List.of());

        partitionedPoller.pollAndProcessEvents();

        assertTrue(event.isProcessed());
        assertNotNull(event.getProcessedAt());
        verify(outboxRepo).save(event);
    }

    @Test
    void shouldHandleNoHandlersList() {
        OutboxProcessor emptyProcessor = new OutboxProcessor(outboxRepo, List.of());
        OutboxPoller emptyPoller = new OutboxPoller(lockRepository, outboxRepo, emptyProcessor);

        OutboxEventJpaEntity event = new OutboxEventJpaEntity(
                "id", "Transfer", "123", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, null);

        stubUnprocessed(List.of(event));

        emptyPoller.pollAndProcessEvents();

        assertNotNull(event.getLastError());
        assertTrue(event.getLastError().contains("No handler for event type: TransferCompletedEvent"));
    }

}
