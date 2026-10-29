package com.bank.app.infrastructure.adapter.out.event;

import com.bank.app.audit.application.port.out.SaveAuditLogPort;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.audit.domain.AuditLog;
import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.common.application.port.out.OutboxPort;
import com.bank.app.common.domain.event.AuditEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditRetryOutboxRelay")
class AuditRetryOutboxRelayTest {

    @Mock
    private SaveAuditLogPort saveAuditLogPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private IdempotencyPort idempotencyPort;

    private ObjectMapper objectMapper;
    private AuditRetryOutboxRelay relay;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.findAndRegisterModules();
        relay = new AuditRetryOutboxRelay(objectMapper, saveAuditLogPort, eventPublisher,
                idempotencyPort, java.time.Clock::systemUTC);
    }

    @Test
    @DisplayName("supports simple and fully-qualified AuditEvent names")
    void shouldSupportAuditEventTypes() {
        assertTrue(relay.supports("AuditEvent"));
        assertTrue(relay.supports(AuditEvent.class.getName()));
        assertFalse(relay.supports("UserRegisteredEvent"));
        assertFalse(relay.supports(""));
        assertFalse(relay.supports(null));
    }

    @Test
    @DisplayName("persists the audit row and dispatches the observation event")
    void shouldPersistAndDispatch() throws Exception {
        AuditEvent event = new AuditEvent(AuditEvent.LOGOUT, "logged out",
                LocalDateTime.now(), "alice", 7L);
        String json = objectMapper.writeValueAsString(event);
        OutboxPort.EventEntry entry = new OutboxPort.EventEntry("evt-1", "Audit",
                "7", "AuditEvent", json, 0, false, false, null, 0, LocalDateTime.now());

        when(idempotencyPort.tryCreateHandler(
                eq("outbox_handler_AuditRetryOutboxRelay_evt-1"), any(LocalDateTime.class)))
                .thenReturn(true);

        relay.handle(entry);

        verify(saveAuditLogPort).save(argThat((AuditLog log) ->
                log.getAction() == AuditAction.LOGOUT
                        && "alice".equals(log.getUsername())
                        && Long.valueOf(7L).equals(log.getActorUserId())));
        verify(eventPublisher).publishEvent(event);
        verify(idempotencyPort).markCompleted(
                "outbox_handler_AuditRetryOutboxRelay_evt-1", "AuditEvent", 200);
    }

    @Test
    @DisplayName("skips a duplicate deferred audit event")
    void shouldSkipDuplicate() throws Exception {
        AuditEvent event = new AuditEvent(AuditEvent.LOGOUT, "logged out",
                LocalDateTime.now(), "alice");
        String json = objectMapper.writeValueAsString(event);
        OutboxPort.EventEntry entry = new OutboxPort.EventEntry("evt-1", "Audit",
                "7", "AuditEvent", json, 0, false, false, null, 0, LocalDateTime.now());

        when(idempotencyPort.tryCreateHandler(any(), any(LocalDateTime.class))).thenReturn(false);
        when(idempotencyPort.findById("outbox_handler_AuditRetryOutboxRelay_evt-1"))
                .thenReturn(Optional.of(new IdempotencyPort.Entry(
                        "outbox_handler_AuditRetryOutboxRelay_evt-1", "COMPLETED",
                        "AuditEvent", 200, LocalDateTime.now())));

        relay.handle(entry);

        verifyNoInteractions(saveAuditLogPort, eventPublisher);
    }

    @Test
    @DisplayName("fails the outbox entry when the deferred write still fails")
    void shouldFailWhenDeferredWriteFails() throws Exception {
        AuditEvent event = new AuditEvent(AuditEvent.LOGOUT, "logged out",
                LocalDateTime.now(), "alice");
        String json = objectMapper.writeValueAsString(event);
        OutboxPort.EventEntry entry = new OutboxPort.EventEntry("evt-1", "Audit",
                "7", "AuditEvent", json, 0, false, false, null, 0, LocalDateTime.now());

        when(idempotencyPort.tryCreateHandler(any(), any(LocalDateTime.class))).thenReturn(true);
        doThrow(new RuntimeException("audit db still down")).when(saveAuditLogPort).save(any());

        RuntimeException failure = assertThrows(RuntimeException.class, () -> relay.handle(entry));

        assertTrue(failure.getMessage().contains("AuditRetryOutboxRelay failed"));
        verify(idempotencyPort).markFailed("outbox_handler_AuditRetryOutboxRelay_evt-1");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("fails the outbox entry when the payload is malformed")
    void shouldFailOnMalformedPayload() {
        OutboxPort.EventEntry entry = new OutboxPort.EventEntry("evt-2", "Audit",
                "7", "AuditEvent", "{invalid json}", 0, false, false, null, 0, LocalDateTime.now());

        when(idempotencyPort.tryCreateHandler(any(), any(LocalDateTime.class))).thenReturn(true);

        RuntimeException failure = assertThrows(RuntimeException.class, () -> relay.handle(entry));

        assertTrue(failure.getMessage().contains("AuditRetryOutboxRelay failed"));
        verifyNoInteractions(saveAuditLogPort, eventPublisher);
    }
}
