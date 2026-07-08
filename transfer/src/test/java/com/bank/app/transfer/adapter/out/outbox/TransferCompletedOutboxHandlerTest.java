package com.bank.app.transfer.adapter.out.outbox;

import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.common.application.port.out.OutboxPort;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import com.bank.app.transfer.domain.TransferCompletedEvent;
import com.bank.app.transfer.domain.TransferStatus;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferCompletedOutboxHandler")
class TransferCompletedOutboxHandlerTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private IdempotencyPort idempotencyPort;

    private ObjectMapper objectMapper;
    private TransferCompletedOutboxHandler handler;

    @Captor
    private ArgumentCaptor<AsyncTransferCompletedEvent> eventCaptor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.findAndRegisterModules();
        handler = new TransferCompletedOutboxHandler(objectMapper, eventPublisher, idempotencyPort);
    }

    @Nested
    @DisplayName("supports")
    class Supports {

        @Test
        @DisplayName("should return true for TransferCompletedEvent")
        void shouldReturnTrueForTransferCompletedEvent() {
            assertTrue(handler.supports("TransferCompletedEvent"));
        }

        @Test
        @DisplayName("should return false for unknown event type")
        void shouldReturnFalseForUnknown() {
            assertFalse(handler.supports("UnknownEvent"));
            assertFalse(handler.supports(""));
            assertFalse(handler.supports(null));
        }
    }

    @Nested
    @DisplayName("handle")
    class Handle {

        private void mockDedupSuccess(String eventId) {
            when(idempotencyPort.tryCreate(
                    eq("outbox_handler_TransferCompletedOutboxHandler_" + eventId),
                    any(LocalDateTime.class)))
                    .thenReturn(true);
        }

        @Test
        @DisplayName("should publish AsyncTransferCompletedEvent on success")
        void shouldPublishEventSuccessfully() throws Exception {
            String json = objectMapper.writeValueAsString(new TransferCompletedEvent(
                    42L, 1L, 2L,
                    new Money(new BigDecimal("250.00"), Currency.TRY),
                    TransferStatus.COMPLETED, LocalDateTime.now()));
            OutboxPort.EventEntry event = new OutboxPort.EventEntry("evt-1", "Transfer",
                    "42", "TransferCompletedEvent", json, 0, false, false, null, 0, LocalDateTime.now());

            mockDedupSuccess("evt-1");
            handler.handle(event);

            verify(eventPublisher).publishEvent(eventCaptor.capture());
            AsyncTransferCompletedEvent captured = eventCaptor.getValue();
            assertEquals(42L, captured.transferId());
            assertEquals(1L, captured.senderAccountId());
            assertEquals(2L, captured.receiverAccountId());
            assertEquals(new Money(new BigDecimal("250.00"), Currency.TRY), captured.amount());
            assertEquals(TransferStatus.COMPLETED, captured.status());
        }

        @Test
        @DisplayName("should throw RuntimeException when payload is malformed")
        void shouldThrowOnMalformedPayload() {
            OutboxPort.EventEntry event = new OutboxPort.EventEntry("evt-2", "Transfer",
                    "42", "TransferCompletedEvent", "{invalid json}", 0, false, false, null, 0, LocalDateTime.now());

            mockDedupSuccess("evt-2");
            RuntimeException ex = assertThrows(RuntimeException.class, () -> handler.handle(event));
            assertTrue(ex.getMessage().contains("TransferCompletedOutboxHandler failed"));
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("should throw RuntimeException when payload is null")
        void shouldThrowOnNullPayload() {
            OutboxPort.EventEntry event = new OutboxPort.EventEntry("evt-3", "Transfer",
                    "42", "TransferCompletedEvent", null, 0, false, false, null, 0, LocalDateTime.now());

            mockDedupSuccess("evt-3");
            RuntimeException ex = assertThrows(RuntimeException.class, () -> handler.handle(event));
            assertTrue(ex.getMessage().contains("TransferCompletedOutboxHandler failed"));
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("should throw RuntimeException when publisher fails")
        void shouldThrowWhenPublisherFails() throws Exception {
            String json = objectMapper.writeValueAsString(new TransferCompletedEvent(
                    99L, 1L, 2L,
                    new Money(new BigDecimal("100.00"), Currency.USD),
                    TransferStatus.COMPLETED, LocalDateTime.now()));
            OutboxPort.EventEntry event = new OutboxPort.EventEntry("evt-4", "Transfer",
                    "99", "TransferCompletedEvent", json, 0, false, false, null, 0, LocalDateTime.now());

            mockDedupSuccess("evt-4");
            doThrow(new RuntimeException("publisher error")).when(eventPublisher).publishEvent(any());

            RuntimeException ex = assertThrows(RuntimeException.class, () -> handler.handle(event));
            assertTrue(ex.getMessage().contains("TransferCompletedOutboxHandler failed"));
        }

        @Test
        @DisplayName("should skip duplicate event when idempotency key already exists")
        void shouldSkipDuplicateEvent() throws Exception {
            String json = objectMapper.writeValueAsString(new TransferCompletedEvent(
                    42L, 1L, 2L,
                    new Money(new BigDecimal("250.00"), Currency.TRY),
                    TransferStatus.COMPLETED, LocalDateTime.now()));
            OutboxPort.EventEntry event = new OutboxPort.EventEntry("evt-5", "Transfer",
                    "42", "TransferCompletedEvent", json, 0, false, false, null, 0, LocalDateTime.now());

            when(idempotencyPort.tryCreate(eq("outbox_handler_TransferCompletedOutboxHandler_evt-5"), any(LocalDateTime.class)))
                    .thenReturn(false);

            handler.handle(event);

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("should process event when idempotency key is created successfully")
        void shouldProcessWhenIdempotencyKeyCreated() throws Exception {
            String json = objectMapper.writeValueAsString(new TransferCompletedEvent(
                    42L, 1L, 2L,
                    new Money(new BigDecimal("250.00"), Currency.TRY),
                    TransferStatus.COMPLETED, LocalDateTime.now()));
            OutboxPort.EventEntry event = new OutboxPort.EventEntry("evt-6", "Transfer",
                    "42", "TransferCompletedEvent", json, 0, false, false, null, 0, LocalDateTime.now());

            when(idempotencyPort.tryCreate(eq("outbox_handler_TransferCompletedOutboxHandler_evt-6"), any(LocalDateTime.class)))
                    .thenReturn(true);

            handler.handle(event);

            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertEquals(42L, eventCaptor.getValue().transferId());
        }
    }
}
