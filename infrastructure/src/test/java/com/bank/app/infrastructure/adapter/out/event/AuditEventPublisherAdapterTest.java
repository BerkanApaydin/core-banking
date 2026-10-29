package com.bank.app.infrastructure.adapter.out.event;

import com.bank.app.common.application.port.out.TransactionBoundaryPort;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.audit.application.port.out.SaveAuditLogPort;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.audit.domain.AuditLog;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

    @Mock
    private TransactionBoundaryPort transactionBoundary;

    @Mock
    private Executor bestEffortExecutor;

    @InjectMocks
    private AuditEventPublisherAdapter adapter;

    @BeforeEach
    void setUp() {
        lenient().when(transactionBoundary.executeRequiresNew(any(), anyInt()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
    }

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
         verify(eventPublisher, never()).publishEvent(any(Object.class));
     }

     @Test
     void shouldPersistAndDispatchWhenBestEffortWriteSucceeds() {
         AuditEvent event = new AuditEvent("LOGOUT", "logged out", LocalDateTime.now(), "alice");

         adapter.publishBestEffort(event);

         verify(transactionBoundary).executeRequiresNew(any(), anyInt());
         verify(saveAuditLogPort).save(any(AuditLog.class));
         verify(retryOutboxAdapter, never()).enqueue(any());
         verify(eventPublisher).publishEvent(event);
     }

     @Test
     void shouldUseCallerTransactionForMandatoryPublish() {
         AuditEvent event = new AuditEvent("TRANSFER_EXECUTED", "test detail", LocalDateTime.now(), "alice");

         adapter.publish(event);

         verify(transactionBoundary, never()).executeRequiresNew(any(), anyInt());
     }

    @Test
    void shouldDeferBestEffortWriteUntilAfterCallerTransactionCompletes() {
        AuditEvent event = new AuditEvent("PASSWORD_RESET_REQUESTED", "reset", LocalDateTime.now(), "alice");
        when(transactionBoundary.isTransactionActive()).thenReturn(true);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(transactionBoundary).runAfterCompletion(any());
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(bestEffortExecutor).execute(any());

        adapter.publishBestEffort(event);

        verify(saveAuditLogPort).save(any(AuditLog.class));
        verify(eventPublisher).publishEvent(event);
    }

    @Test
    void shouldNotWriteBeforeCallerTransactionCompletes() {
        AuditEvent event = new AuditEvent("PASSWORD_RESET_REQUESTED", "reset", LocalDateTime.now(), "alice");
        when(transactionBoundary.isTransactionActive()).thenReturn(true);

        adapter.publishBestEffort(event);

        verifyNoInteractions(saveAuditLogPort, bestEffortExecutor);
        verify(transactionBoundary).runAfterCompletion(any());
    }

    @Test
    void shouldNotFailCallerWhenBestEffortExecutorRejectsEvent() {
        AuditEvent event = new AuditEvent("PASSWORD_RESET_REQUESTED", "reset", LocalDateTime.now(), "alice");
        when(transactionBoundary.isTransactionActive()).thenReturn(true);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(transactionBoundary).runAfterCompletion(any());
        doThrow(new RejectedExecutionException("queue full")).when(bestEffortExecutor).execute(any());

        assertDoesNotThrow(() -> adapter.publishBestEffort(event));

        verifyNoInteractions(saveAuditLogPort);
        verify(retryOutboxAdapter, never()).enqueue(any());
    }

    @Test
    void shouldQueueDurableRetryWhenBestEffortTransactionCannotStart() {
         AuditEvent event = new AuditEvent("LOGOUT", "logged out", LocalDateTime.now(), "alice");
         doThrow(new RuntimeException("transaction manager down"))
                 .when(transactionBoundary).executeRequiresNew(any(), anyInt());

         assertDoesNotThrow(() -> adapter.publishBestEffort(event));

         verify(retryOutboxAdapter).enqueue(event);
         verify(eventPublisher, never()).publishEvent(any(Object.class));
     }

     @Test
     void shouldNotFailCallerWhenRetryEnqueueAlsoFails() {
         AuditEvent event = new AuditEvent("LOGOUT", "logged out", LocalDateTime.now(), "alice");
         doThrow(new RuntimeException("audit db down")).when(saveAuditLogPort).save(any());
         doThrow(new RuntimeException("outbox down")).when(retryOutboxAdapter).enqueue(any());

         assertDoesNotThrow(() -> adapter.publishBestEffort(event));

         verify(eventPublisher, never()).publishEvent(any(Object.class));
     }

     @Test
     void shouldNotQueueRetryWhenOnlyObservationDispatchFails() {
         AuditEvent event = new AuditEvent("LOGOUT", "logged out", LocalDateTime.now(), "alice");
         doThrow(new RuntimeException("observer down")).when(eventPublisher).publishEvent(any(Object.class));

         assertDoesNotThrow(() -> adapter.publishBestEffort(event));

         verify(retryOutboxAdapter, never()).enqueue(any());
     }
 }
