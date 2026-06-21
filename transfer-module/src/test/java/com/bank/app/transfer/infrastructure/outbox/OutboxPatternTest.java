package com.bank.app.transfer.infrastructure.outbox;

import com.bank.app.common.outbox.*;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferCompletedEvent;
import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import com.bank.app.common.domain.Money;
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

    @BeforeEach
    void setUp() {
        outboxRepo = mock(OutboxEventJpaRepository.class);
        lockRepository = mock(OutboxLockRepository.class);
        objectMapper = new ObjectMapper();
        eventPublisher = mock(ApplicationEventPublisher.class);

        eventListener = new OutboxEventListener(outboxRepo, objectMapper);
        handler = new TransferCompletedOutboxHandler(objectMapper, eventPublisher);
        outboxPoller = new OutboxPoller(lockRepository, outboxRepo, List.of(handler));
        ReflectionTestUtils.setField(outboxPoller, "maxRetries", 5);
    }

    @Test
    void shouldSaveOutboxEventWhenTransferCompletedEventFired() throws Exception {
        Transfer transfer = new Transfer(
                123L, 1L, 2L,
                new Money(new BigDecimal("100.00"), Money.Currency.TRY),
                TransferStatus.COMPLETED,
                LocalDateTime.now());
        TransferCompletedEvent event = new TransferCompletedEvent(transfer);

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

        when(lockRepository.findAndLockUnprocessed(50, -1)).thenReturn(List.of(entity));

        outboxPoller.pollAndProcessEvents();

        ArgumentCaptor<AsyncTransferCompletedEvent> eventCaptor = ArgumentCaptor
                .forClass(AsyncTransferCompletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        AsyncTransferCompletedEvent asyncEvent = eventCaptor.getValue();
        assertEquals(123L, asyncEvent.transfer().getId());
        assertEquals(1L, asyncEvent.transfer().getSenderAccountId());
        assertEquals(2L, asyncEvent.transfer().getReceiverAccountId());

        assertTrue(entity.isProcessed());
        assertNotNull(entity.getProcessedAt());
        verify(outboxRepo).save(entity);
    }

    @Test
    void shouldIncrementRetryCountWhenProcessingFails() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                "event-uuid", "Transfer", "123", "TransferCompletedEvent",
                "invalid json string", LocalDateTime.now(), false, null);

        when(lockRepository.findAndLockUnprocessed(50, -1)).thenReturn(List.of(entity));

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

        when(lockRepository.findAndLockUnprocessed(50, -1)).thenReturn(List.of(entity));

        outboxPoller.pollAndProcessEvents();

        assertTrue(entity.isDeadLetter());
        assertEquals(5, entity.getRetryCount());
        verify(outboxRepo).save(entity);
    }

    @Test
    void shouldSkipProcessingWhenNoUnprocessedEvents() {
        when(lockRepository.findAndLockUnprocessed(50, -1)).thenReturn(List.of());

        outboxPoller.pollAndProcessEvents();

        verifyNoInteractions(eventPublisher);
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void shouldThrowWhenNoHandlerFoundForEventType() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                "event-uuid", "Transfer", "123", "UnknownEventType",
                "{}", LocalDateTime.now(), false, null);

        when(lockRepository.findAndLockUnprocessed(50, -1)).thenReturn(List.of(entity));

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
        assertEquals(456L, published.transfer().getId());
        assertEquals(Money.Currency.USD, published.transfer().getAmount().currency());
    }

    @Test
    void shouldAssignPartitionWhenPartitionCountGreaterThanZero() throws Exception {
        ReflectionTestUtils.setField(eventListener, "partitionCount", 3);

        Transfer transfer = new Transfer(
                5L, 1L, 2L,
                new Money(new BigDecimal("100.00"), Money.Currency.TRY),
                TransferStatus.COMPLETED,
                LocalDateTime.now());
        TransferCompletedEvent event = new TransferCompletedEvent(transfer);

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
                new Money(new BigDecimal("100.00"), Money.Currency.TRY),
                TransferStatus.COMPLETED,
                LocalDateTime.now());
        TransferCompletedEvent event = new TransferCompletedEvent(transfer);

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

        when(lockRepository.findAndLockUnprocessed(50, -1)).thenReturn(List.of(entity));

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

        when(lockRepository.findAndLockUnprocessed(50, -1))
                .thenReturn(List.of(entity));

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
                List.of(otherHandler, matchingHandler));

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

        when(lockRepository.findAndLockUnprocessed(50, -1))
                .thenReturn(List.of(entity));

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
                List.of(failingHandler));

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

        when(lockRepository.findAndLockUnprocessed(50, -1))
                .thenReturn(List.of(entity));

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
                List.of(failingHandler));

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

        when(lockRepository.findAndLockUnprocessed(50, -1))
                .thenReturn(List.of(entity));

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
                List.of(failingHandler));

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

        when(lockRepository.findAndLockUnprocessed(50, -1))
                .thenReturn(List.of(entity));

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
                List.of(failingHandler));

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

        when(lockRepository.findAndLockUnprocessed(50, -1))
                .thenReturn(List.of(entity));

        poller.pollAndProcessEvents();

        assertNull(entity.getLastError()); // truncate(null)
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
                List.of(failingHandler));

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

        when(lockRepository.findAndLockUnprocessed(50, -1))
                .thenReturn(List.of(entity));

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

        OutboxPoller poller = new OutboxPoller(lockRepository, outboxRepo, List.of(handler1));
        ReflectionTestUtils.setField(poller, "maxRetries", 5);

        when(lockRepository.findAndLockUnprocessed(50, -1))
                .thenReturn(List.of(event1, event2));

        poller.pollAndProcessEvents();

        verify(outboxRepo, times(2)).save(any(OutboxEventJpaEntity.class));
    }

    @Test
    void shouldCreateWithCustomMaxRetries() {
        OutboxPoller pollerWithCustomRetries = new OutboxPoller(lockRepository, outboxRepo, List.of(handler), 3, 50, 0);

        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                "id", "Transfer", "123", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, null,
                2, false, null);

        when(lockRepository.findAndLockUnprocessed(50, -1)).thenReturn(List.of(entity));

        pollerWithCustomRetries.pollAndProcessEvents();

        assertEquals(3, entity.getRetryCount());
        assertTrue(entity.isDeadLetter());
    }

    @Test
    void shouldPollMultiplePartitionsWhenPartitionCountSet() throws Exception {
        OutboxPoller partitionedPoller = new OutboxPoller(lockRepository, outboxRepo, List.of(handler), 5, 10, 3);

        String validPayload = "{\"transferId\":123,\"senderAccountId\":1,\"receiverAccountId\":2,\"amount\":100.00,\"currency\":\"TRY\"}";
        OutboxEventJpaEntity event = new OutboxEventJpaEntity(
                "id", "Transfer", "123", "TransferCompletedEvent",
                validPayload, LocalDateTime.now(), false, null);

        when(lockRepository.findAndLockUnprocessed(10, 0)).thenReturn(List.of(event));
        when(lockRepository.findAndLockUnprocessed(10, 1)).thenReturn(List.of());
        when(lockRepository.findAndLockUnprocessed(10, 2)).thenReturn(List.of());

        partitionedPoller.pollAndProcessEvents();

        assertTrue(event.isProcessed());
        assertNotNull(event.getProcessedAt());
        verify(outboxRepo).save(event);
    }

    @Test
    void shouldHandleNoHandlersList() {
        OutboxPoller emptyPoller = new OutboxPoller(lockRepository, outboxRepo, List.of());

        OutboxEventJpaEntity event = new OutboxEventJpaEntity(
                "id", "Transfer", "123", "TransferCompletedEvent",
                "{}", LocalDateTime.now(), false, null);

        when(lockRepository.findAndLockUnprocessed(50, -1)).thenReturn(List.of(event));

        emptyPoller.pollAndProcessEvents();

        assertNotNull(event.getLastError());
        assertTrue(event.getLastError().contains("No handler for event type: TransferCompletedEvent"));
    }

}
