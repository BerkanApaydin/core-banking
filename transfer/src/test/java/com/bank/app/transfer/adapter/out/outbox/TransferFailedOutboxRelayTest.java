package com.bank.app.transfer.adapter.out.outbox;

import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.common.application.port.out.OutboxPort;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.domain.AsyncTransferFailedEvent;
import com.bank.app.transfer.domain.TransferFailedEvent;
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
import com.bank.app.transfer.adapter.in.event.TransferEventConsumer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferFailedOutboxRelay")
class TransferFailedOutboxRelayTest {

    @Mock
    private TransferEventConsumer notificationConsumer;

    @Mock
    private IdempotencyPort idempotencyPort;

    private ObjectMapper objectMapper;
    private TransferFailedOutboxRelay handler;

    @Captor
    private ArgumentCaptor<AsyncTransferFailedEvent> eventCaptor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.findAndRegisterModules();
        handler = new TransferFailedOutboxRelay(objectMapper, notificationConsumer, idempotencyPort, Clock::systemUTC);
    }

    @Nested
    @DisplayName("supports")
    class Supports {

        @Test
        @DisplayName("should return true for TransferFailedEvent")
        void shouldReturnTrueForTransferFailedEvent() {
            assertTrue(handler.supports("TransferFailedEvent"));
        }

        @Test
        @DisplayName("should return false for unknown event type")
        void shouldReturnFalseForUnknown() {
            assertFalse(handler.supports("UnknownEvent"));
            assertFalse(handler.supports("TransferCompletedEvent"));
            assertFalse(handler.supports(null));
        }
    }

    @Nested
    @DisplayName("handle")
    class Handle {

        private void mockDedupSuccess(String eventId) {
            when(idempotencyPort.tryCreateHandler(
                    eq("outbox_handler_TransferFailedOutboxRelay_" + eventId),
                    any(LocalDateTime.class)))
                    .thenReturn(true);
        }

        @Test
        @DisplayName("should publish AsyncTransferFailedEvent on success")
        void shouldPublishEventSuccessfully() throws Exception {
            String json = objectMapper.writeValueAsString(new TransferFailedEvent(
                    42L, 1L, 2L,
                    new Money(new BigDecimal("250.00"), Currency.TRY),
                    TransferStatus.FAILED, LocalDateTime.now()));
            OutboxPort.EventEntry event = new OutboxPort.EventEntry("evt-1", "Transfer",
                    "42", "TransferFailedEvent", json, 0, false, false, null, 0, LocalDateTime.now());

            mockDedupSuccess("evt-1");
            handler.handle(event);

            verify(notificationConsumer).handleTransferFailed(eventCaptor.capture());
            AsyncTransferFailedEvent captured = eventCaptor.getValue();
            assertEquals(42L, captured.transferId());
            assertEquals(1L, captured.senderAccountId());
            assertEquals(2L, captured.receiverAccountId());
            assertEquals(new Money(new BigDecimal("250.00"), Currency.TRY), captured.amount());
            assertEquals(TransferStatus.FAILED, captured.status());
        }

        @Test
        @DisplayName("should throw RuntimeException when payload is malformed")
        void shouldThrowOnMalformedPayload() {
            OutboxPort.EventEntry event = new OutboxPort.EventEntry("evt-2", "Transfer",
                    "42", "TransferFailedEvent", "{invalid json}", 0, false, false, null, 0, LocalDateTime.now());

            mockDedupSuccess("evt-2");
            RuntimeException ex = assertThrows(RuntimeException.class, () -> handler.handle(event));
            assertTrue(ex.getMessage().contains("TransferFailedOutboxRelay failed"));
            verify(notificationConsumer, never()).handleTransferFailed(any());
        }

        @Test
        @DisplayName("should skip duplicate event when idempotency key already exists")
        void shouldSkipDuplicateEvent() throws Exception {
            String json = objectMapper.writeValueAsString(new TransferFailedEvent(
                    42L, 1L, 2L,
                    new Money(new BigDecimal("250.00"), Currency.TRY),
                    TransferStatus.FAILED, LocalDateTime.now()));
            OutboxPort.EventEntry event = new OutboxPort.EventEntry("evt-5", "Transfer",
                    "42", "TransferFailedEvent", json, 0, false, false, null, 0, LocalDateTime.now());

            when(idempotencyPort.tryCreateHandler(eq("outbox_handler_TransferFailedOutboxRelay_evt-5"), any(LocalDateTime.class)))
                    .thenReturn(false);

            handler.handle(event);

            verify(notificationConsumer, never()).handleTransferFailed(any());
        }

        @Test
        @DisplayName("should throw RuntimeException when consumer fails")
        void shouldThrowWhenConsumerFails() throws Exception {
            String json = objectMapper.writeValueAsString(new TransferFailedEvent(
                    99L, 1L, 2L,
                    new Money(new BigDecimal("100.00"), Currency.USD),
                    TransferStatus.FAILED, LocalDateTime.now()));
            OutboxPort.EventEntry event = new OutboxPort.EventEntry("evt-4", "Transfer",
                    "99", "TransferFailedEvent", json, 0, false, false, null, 0, LocalDateTime.now());

            mockDedupSuccess("evt-4");
            doThrow(new RuntimeException("consumer error")).when(notificationConsumer).handleTransferFailed(any());

            RuntimeException ex = assertThrows(RuntimeException.class, () -> handler.handle(event));
            assertTrue(ex.getMessage().contains("TransferFailedOutboxRelay failed"));
            // Audit P2: the failed claim must be marked FAILED (not left PENDING)
            // so the next retry reopens it instead of skipping as a duplicate.
            verify(idempotencyPort).markFailed("outbox_handler_TransferFailedOutboxRelay_evt-4");
        }

        @Test
        @DisplayName("should complete the dedup key on success for retention hygiene")
        void shouldCompleteDedupKeyOnSuccess() throws Exception {
            String json = objectMapper.writeValueAsString(new TransferFailedEvent(
                    42L, 1L, 2L,
                    new Money(new BigDecimal("250.00"), Currency.TRY),
                    TransferStatus.FAILED, LocalDateTime.now()));
            OutboxPort.EventEntry event = new OutboxPort.EventEntry("evt-8", "Transfer",
                    "42", "TransferFailedEvent", json, 0, false, false, null, 0, LocalDateTime.now());

            mockDedupSuccess("evt-8");
            handler.handle(event);

            // Retention deletes only non-PENDING handler keys: success must
            // leave COMPLETED behind, never perpetual PENDING.
            verify(idempotencyPort).markCompleted(
                    eq("outbox_handler_TransferFailedOutboxRelay_evt-8"), any(), anyInt());
        }

        @Test
        @DisplayName("should redeliver when the previous attempt failed instead of skipping")
        void shouldRedeliverAfterFailedAttempt() throws Exception {
            String json = objectMapper.writeValueAsString(new TransferFailedEvent(
                    42L, 1L, 2L,
                    new Money(new BigDecimal("250.00"), Currency.TRY),
                    TransferStatus.FAILED, LocalDateTime.now()));
            OutboxPort.EventEntry event = new OutboxPort.EventEntry("evt-9", "Transfer",
                    "42", "TransferFailedEvent", json, 0, false, false, null, 0, LocalDateTime.now());

            when(idempotencyPort.tryCreateHandler(eq("outbox_handler_TransferFailedOutboxRelay_evt-9"), any(LocalDateTime.class)))
                    .thenReturn(false);
            when(idempotencyPort.findById("outbox_handler_TransferFailedOutboxRelay_evt-9"))
                    .thenReturn(Optional.of(new IdempotencyPort.Entry(
                            "outbox_handler_TransferFailedOutboxRelay_evt-9", "FAILED", null, null, LocalDateTime.now())));
            when(idempotencyPort.tryResetFailed(eq("outbox_handler_TransferFailedOutboxRelay_evt-9"), any(LocalDateTime.class)))
                    .thenReturn(true);

            handler.handle(event);

            verify(notificationConsumer).handleTransferFailed(any());
        }
    }
}
