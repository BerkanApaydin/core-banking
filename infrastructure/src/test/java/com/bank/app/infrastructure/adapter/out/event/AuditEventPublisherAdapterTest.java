package com.bank.app.infrastructure.adapter.out.event;

import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.audit.application.port.out.SaveAuditLogPort;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.audit.domain.AuditLog;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditEventPublisherAdapterTest {
 
     @Mock
     private SaveAuditLogPort saveAuditLogPort;

     @Mock
     private ApplicationEventPublisher eventPublisher;

     @Mock
     private AuditRetryOutboxAdapter retryOutboxAdapter;

    @Mock
    private MeterRegistry meterRegistry;

    @InjectMocks
    private AuditEventPublisherAdapter adapter;

    @Test
     void shouldPublishAuditEvent() {
         AuditEvent event = new AuditEvent("TRANSFER_EXECUTED", "test detail", LocalDateTime.now(), "alice");

         adapter.publish(event);

         verify(saveAuditLogPort).save(argThat((AuditLog log) ->
                 log.getAction() == AuditAction.TRANSFER_EXECUTED
                         && log.getUsername().equals("alice")
                         && log.getTimestamp().equals(event.occurredAt())));
     }

     @Test
     void shouldPersistActorUserIdWhenPresent() {
         AuditEvent event = new AuditEvent("LOGIN_SUCCEEDED", "logged in", LocalDateTime.now(), "alice", 7L);

         adapter.publish(event);

         verify(saveAuditLogPort).save(argThat((AuditLog log) ->
                 log.getAction() == AuditAction.LOGIN_SUCCEEDED
                         && Long.valueOf(7L).equals(log.getActorUserId())));
     }

     @Test
     void shouldDispatchEventForAfterCommitObservation() {
         AuditEvent event = new AuditEvent("ACCOUNT_DEBITED", "leg", LocalDateTime.now(), "alice");

         adapter.publish(event);

         // The row is already durable; dispatch lets AuditEventConsumer observe
         // the pipeline end-to-end (metric-only, never a second write).
         verify(eventPublisher).publishEvent(event);
     }

     @Test
     void shouldQueueDurableRetryWhenBestEffortWriteFails() {
         AuditEvent event = new AuditEvent("LOGOUT", "logged out", LocalDateTime.now(), "alice");
         doThrow(new RuntimeException("audit db down")).when(saveAuditLogPort).save(any());

         adapter.publishBestEffort(event);

         verify(retryOutboxAdapter).enqueue(event);
         verify(eventPublisher, never()).publishEvent(any());
     }

     @Test
     void shouldPersistAndDispatchWhenBestEffortWriteSucceeds() {
         AuditEvent event = new AuditEvent("LOGOUT", "logged out", LocalDateTime.now(), "alice");

         adapter.publishBestEffort(event);

         verify(saveAuditLogPort).save(any(AuditLog.class));
         verify(retryOutboxAdapter, never()).enqueue(any());
         verify(eventPublisher).publishEvent(event);
     }

     @Test
     void shouldNotFailCallerWhenRetryEnqueueAlsoFails() {
         AuditEvent event = new AuditEvent("LOGOUT", "logged out", LocalDateTime.now(), "alice");
         doThrow(new RuntimeException("audit db down")).when(saveAuditLogPort).save(any());
         doThrow(new RuntimeException("outbox down")).when(retryOutboxAdapter).enqueue(any());

         assertDoesNotThrow(() -> adapter.publishBestEffort(event));

         verify(eventPublisher, never()).publishEvent(any());
     }

     @Test
     void shouldNotQueueRetryWhenOnlyObservationDispatchFails() {
         AuditEvent event = new AuditEvent("LOGOUT", "logged out", LocalDateTime.now(), "alice");
         doThrow(new RuntimeException("observer down")).when(eventPublisher).publishEvent(any());

         assertDoesNotThrow(() -> adapter.publishBestEffort(event));

         verify(retryOutboxAdapter, never()).enqueue(any());
     }
 }
