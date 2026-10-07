package com.bank.app.audit.adapter.in.event;

import com.bank.app.audit.application.port.out.AuditFailurePort;
import com.bank.app.audit.application.port.out.AuditObservationPort;
import com.bank.app.common.domain.event.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The consumer is metric-only by design: the audit row is persisted
 * synchronously by the AuditEventPort implementation, so this listener must
 * never write — only observe that the event completed the pipeline.
 */
@ExtendWith(MockitoExtension.class)
class AuditEventConsumerTest {

    @Mock
    private AuditObservationPort observationPort;

    @Mock
    private AuditFailurePort auditFailurePort;

    private AuditEventConsumer eventListener;

    @BeforeEach
    void setUp() {
        eventListener = new AuditEventConsumer(observationPort, auditFailurePort);
    }

    @Test
    void shouldObserveAuditEventWithoutWriting() {
        AuditEvent event = new AuditEvent("ACCOUNT_CREATED", "New account created", LocalDateTime.now());

        eventListener.onAuditEvent(event);

        verify(observationPort).recordConsumed(eq("ACCOUNT_CREATED"));
        verifyNoInteractions(auditFailurePort);
    }

    @Test
    void shouldObserveTransferExecuted() {
        AuditEvent event = new AuditEvent("TRANSFER_EXECUTED", "Transfer executed", LocalDateTime.now());

        eventListener.onAuditEvent(event);

        verify(observationPort).recordConsumed(eq("TRANSFER_EXECUTED"));
        verifyNoInteractions(auditFailurePort);
    }

    @Test
    void shouldObserveTransferCancelled() {
        AuditEvent event = new AuditEvent("TRANSFER_CANCELLED", "Transfer cancelled", LocalDateTime.now());

        eventListener.onAuditEvent(event);

        verify(observationPort).recordConsumed(eq("TRANSFER_CANCELLED"));
        verifyNoInteractions(auditFailurePort);
    }

    @Test
    void shouldObserveAccountDebited() {
        AuditEvent event = new AuditEvent("ACCOUNT_DEBITED", "Amount withdrawn from account", LocalDateTime.now());

        eventListener.onAuditEvent(event);

        verify(observationPort).recordConsumed(eq("ACCOUNT_DEBITED"));
        verifyNoInteractions(auditFailurePort);
    }

    @Test
    void shouldObserveAccountCredited() {
        AuditEvent event = new AuditEvent("ACCOUNT_CREDITED", "Amount deposited to account", LocalDateTime.now());

        eventListener.onAuditEvent(event);

        verify(observationPort).recordConsumed(eq("ACCOUNT_CREDITED"));
        verifyNoInteractions(auditFailurePort);
    }

    @Test
    void shouldObserveAccountSuspended() {
        AuditEvent event = new AuditEvent("ACCOUNT_SUSPENDED", "Hesap donduruldu", LocalDateTime.now());

        eventListener.onAuditEvent(event);

        verify(observationPort).recordConsumed(eq("ACCOUNT_SUSPENDED"));
        verifyNoInteractions(auditFailurePort);
    }

    @Test
    void shouldObserveAccountClosed() {
        AuditEvent event = new AuditEvent("ACCOUNT_CLOSED", "Account closed", LocalDateTime.now());

        eventListener.onAuditEvent(event);

        verify(observationPort).recordConsumed(eq("ACCOUNT_CLOSED"));
        verifyNoInteractions(auditFailurePort);
    }

    @Test
    void shouldRecordFailureMetricWhenObservationFails() {
        AuditEvent event = new AuditEvent("TRANSFER_CANCELLED", "Transfer cancelled", LocalDateTime.now());
        doThrow(new RuntimeException("metrics down")).when(observationPort).recordConsumed(eq("TRANSFER_CANCELLED"));

        eventListener.onAuditEvent(event);

        verify(auditFailurePort).recordFailure(eq("TRANSFER_CANCELLED"), eq("RuntimeException"));
    }

    @Test
    void shouldRecordFailureForUnmappedAction() {
        AuditEvent event = new AuditEvent("TRANSFER_EXECUTD", "typo'd action", LocalDateTime.now());

        eventListener.onAuditEvent(event);

        verify(auditFailurePort).recordFailure(eq("TRANSFER_EXECUTD"), eq("UnknownAuditActionException"));
        verifyNoInteractions(observationPort);
    }

    @Test
    void shouldPropagatePublishTimeUsernameWithoutUsingIt() {
        // Username travels on the event for the synchronous persistence path;
        // the observer ignores it — assert it never influences the observation.
        AuditEvent event = new AuditEvent("ACCOUNT_CREATED", "New account created", LocalDateTime.now(), "alice");

        eventListener.onAuditEvent(event);

        verify(observationPort).recordConsumed(eq("ACCOUNT_CREATED"));
        verifyNoInteractions(auditFailurePort);
    }
}
