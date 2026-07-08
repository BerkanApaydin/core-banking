package com.bank.app.account.adapter.out.outbox;

import com.bank.app.account.domain.AccountClosedEvent;
import com.bank.app.account.domain.AccountCreatedEvent;
import com.bank.app.account.domain.AccountCreditedEvent;
import com.bank.app.account.domain.AccountDebitedEvent;
import com.bank.app.account.domain.AccountSuspendedEvent;
import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.common.application.port.out.OutboxPort;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.UserId;
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
@DisplayName("AccountEventOutboxHandler")
class AccountEventOutboxHandlerTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private IdempotencyPort idempotencyPort;

    private ObjectMapper objectMapper;
    private AccountEventOutboxHandler handler;

    @Captor
    private ArgumentCaptor<Object> eventCaptor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.findAndRegisterModules();
        handler = new AccountEventOutboxHandler(objectMapper, eventPublisher, idempotencyPort);
    }

    @Nested
    @DisplayName("supports")
    class Supports {

        @Test
        @DisplayName("should return true for all supported event types")
        void shouldReturnTrueForSupportedEvents() {
            assertTrue(handler.supports("AccountCreatedEvent"));
            assertTrue(handler.supports("AccountDebitedEvent"));
            assertTrue(handler.supports("AccountCreditedEvent"));
            assertTrue(handler.supports("AccountSuspendedEvent"));
            assertTrue(handler.supports("AccountClosedEvent"));
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
                    eq("outbox_handler_AccountEventOutboxHandler_" + eventId),
                    any(LocalDateTime.class)))
                    .thenReturn(true);
        }

        @Test
        @DisplayName("should publish AccountCreatedEvent on success")
        void shouldPublishAccountCreatedEvent() throws Exception {
            AccountCreatedEvent domainEvent = new AccountCreatedEvent(
                    1L, new UserId(100L),                     new Iban("TR290006200000000000000111"), "TEST", Money.of(new BigDecimal("1000.00"), Currency.TRY), LocalDateTime.now());
            String json = objectMapper.writeValueAsString(domainEvent);
            OutboxPort.EventEntry event = new OutboxPort.EventEntry("evt-1", "Account",
                    "1", "AccountCreatedEvent", json, 0, false, false, null, 0, LocalDateTime.now());

            mockDedupSuccess("evt-1");
            handler.handle(event);

            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertInstanceOf(AccountCreatedEvent.class, eventCaptor.getValue());
            AccountCreatedEvent captured = (AccountCreatedEvent) eventCaptor.getValue();
            assertEquals(1L, captured.accountId());
        }

        @Test
        @DisplayName("should publish AccountDebitedEvent on success")
        void shouldPublishAccountDebitedEvent() throws Exception {
            AccountDebitedEvent domainEvent = new AccountDebitedEvent(
                    1L, Money.of(new BigDecimal("50.00"), Currency.TRY), Money.of(new BigDecimal("950.00"), Currency.TRY), LocalDateTime.now());
            String json = objectMapper.writeValueAsString(domainEvent);
            OutboxPort.EventEntry event = new OutboxPort.EventEntry("evt-2", "Account",
                    "1", "AccountDebitedEvent", json, 0, false, false, null, 0, LocalDateTime.now());

            mockDedupSuccess("evt-2");
            handler.handle(event);

            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertInstanceOf(AccountDebitedEvent.class, eventCaptor.getValue());
            AccountDebitedEvent captured = (AccountDebitedEvent) eventCaptor.getValue();
            assertEquals(1L, captured.accountId());
        }

        @Test
        @DisplayName("should publish AccountCreditedEvent on success")
        void shouldPublishAccountCreditedEvent() throws Exception {
            AccountCreditedEvent domainEvent = new AccountCreditedEvent(
                    2L, Money.of(new BigDecimal("100.00"), Currency.TRY), Money.of(new BigDecimal("1100.00"), Currency.TRY), LocalDateTime.now());
            String json = objectMapper.writeValueAsString(domainEvent);
            OutboxPort.EventEntry event = new OutboxPort.EventEntry("evt-3", "Account",
                    "2", "AccountCreditedEvent", json, 0, false, false, null, 0, LocalDateTime.now());

            mockDedupSuccess("evt-3");
            handler.handle(event);

            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertInstanceOf(AccountCreditedEvent.class, eventCaptor.getValue());
            AccountCreditedEvent captured = (AccountCreditedEvent) eventCaptor.getValue();
            assertEquals(2L, captured.accountId());
        }

        @Test
        @DisplayName("should publish AccountSuspendedEvent on success")
        void shouldPublishAccountSuspendedEvent() throws Exception {
            AccountSuspendedEvent domainEvent = new AccountSuspendedEvent(
                    3L, LocalDateTime.now());
            String json = objectMapper.writeValueAsString(domainEvent);
            OutboxPort.EventEntry event = new OutboxPort.EventEntry("evt-4", "Account",
                    "3", "AccountSuspendedEvent", json, 0, false, false, null, 0, LocalDateTime.now());

            mockDedupSuccess("evt-4");
            handler.handle(event);

            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertInstanceOf(AccountSuspendedEvent.class, eventCaptor.getValue());
            AccountSuspendedEvent captured = (AccountSuspendedEvent) eventCaptor.getValue();
            assertEquals(3L, captured.accountId());
        }

        @Test
        @DisplayName("should publish AccountClosedEvent on success")
        void shouldPublishAccountClosedEvent() throws Exception {
            AccountClosedEvent domainEvent = new AccountClosedEvent(
                    4L, Money.of(new BigDecimal("500.00"), Currency.TRY), LocalDateTime.now());
            String json = objectMapper.writeValueAsString(domainEvent);
            OutboxPort.EventEntry event = new OutboxPort.EventEntry("evt-5", "Account",
                    "4", "AccountClosedEvent", json, 0, false, false, null, 0, LocalDateTime.now());

            mockDedupSuccess("evt-5");
            handler.handle(event);

            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertInstanceOf(AccountClosedEvent.class, eventCaptor.getValue());
            AccountClosedEvent captured = (AccountClosedEvent) eventCaptor.getValue();
            assertEquals(4L, captured.accountId());
        }

        @Test
        @DisplayName("should log warning and return for unsupported event type")
        void shouldHandleUnsupportedEventType() {
            OutboxPort.EventEntry event = new OutboxPort.EventEntry("evt-6", "Account",
                    "5", "UnknownEventType", "{}", 0, false, false, null, 0, LocalDateTime.now());

            handler.handle(event);

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("should throw RuntimeException when payload is malformed")
        void shouldThrowOnMalformedPayload() {
            OutboxPort.EventEntry event = new OutboxPort.EventEntry("evt-7", "Account",
                    "1", "AccountCreatedEvent", "{invalid json}", 0, false, false, null, 0, LocalDateTime.now());

            mockDedupSuccess("evt-7");
            RuntimeException ex = assertThrows(RuntimeException.class, () -> handler.handle(event));
            assertTrue(ex.getMessage().contains("AccountEventOutboxHandler failed"));
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("should throw RuntimeException when payload is null")
        void shouldThrowOnNullPayload() {
            OutboxPort.EventEntry event = new OutboxPort.EventEntry("evt-8", "Account",
                    "1", "AccountCreatedEvent", null, 0, false, false, null, 0, LocalDateTime.now());

            mockDedupSuccess("evt-8");
            RuntimeException ex = assertThrows(RuntimeException.class, () -> handler.handle(event));
            assertTrue(ex.getMessage().contains("AccountEventOutboxHandler failed"));
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("should skip duplicate event when idempotency key already exists")
        void shouldSkipDuplicateEvent() throws Exception {
            AccountCreatedEvent domainEvent = new AccountCreatedEvent(
                    1L, new UserId(100L), new Iban("TR290006200000000000000111"), "TEST",
                    Money.of(new BigDecimal("1000.00"), Currency.TRY), LocalDateTime.now());
            String json = objectMapper.writeValueAsString(domainEvent);
            OutboxPort.EventEntry event = new OutboxPort.EventEntry("evt-9", "Account",
                    "1", "AccountCreatedEvent", json, 0, false, false, null, 0, LocalDateTime.now());

            when(idempotencyPort.tryCreate(eq("outbox_handler_AccountEventOutboxHandler_evt-9"), any(LocalDateTime.class)))
                    .thenReturn(false);

            handler.handle(event);

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("should process event when idempotency key is created successfully")
        void shouldProcessWhenIdempotencyKeyCreated() throws Exception {
            AccountCreatedEvent domainEvent = new AccountCreatedEvent(
                    1L, new UserId(100L), new Iban("TR290006200000000000000111"), "TEST",
                    Money.of(new BigDecimal("1000.00"), Currency.TRY), LocalDateTime.now());
            String json = objectMapper.writeValueAsString(domainEvent);
            OutboxPort.EventEntry event = new OutboxPort.EventEntry("evt-10", "Account",
                    "1", "AccountCreatedEvent", json, 0, false, false, null, 0, LocalDateTime.now());

            when(idempotencyPort.tryCreate(eq("outbox_handler_AccountEventOutboxHandler_evt-10"), any(LocalDateTime.class)))
                    .thenReturn(true);

            handler.handle(event);

            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertInstanceOf(AccountCreatedEvent.class, eventCaptor.getValue());
        }
    }
}
