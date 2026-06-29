package com.bank.app.audit.adapter.in.event;

import com.bank.app.audit.application.port.in.AuditLoggerUseCase;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.common.domain.event.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditEventListenerTest {

    @Mock
    private AuditLoggerUseCase auditLogger;

    private AuditEventListener eventListener;

    @BeforeEach
    void setUp() {
        eventListener = new AuditEventListener(auditLogger);
    }

    @Test
    void shouldLogAuditEvent() {
        AuditEvent event = new AuditEvent("ACCOUNT_CREATED", "New account created", LocalDateTime.now());

        eventListener.onAuditEvent(event);

        verify(auditLogger).log(eq(AuditAction.ACCOUNT_CREATED), eq("New account created"));
    }

    @Test
    void shouldLogTransferExecuted() {
        AuditEvent event = new AuditEvent("TRANSFER_EXECUTED", "Transfer executed", LocalDateTime.now());

        eventListener.onAuditEvent(event);

        verify(auditLogger).log(eq(AuditAction.TRANSFER_EXECUTED), eq("Transfer executed"));
    }

    @Test
    void shouldLogTransferCancelled() {
        AuditEvent event = new AuditEvent("TRANSFER_CANCELLED", "Transfer cancelled", LocalDateTime.now());

        eventListener.onAuditEvent(event);

        verify(auditLogger).log(eq(AuditAction.TRANSFER_CANCELLED), eq("Transfer cancelled"));
    }

    @Test
    void shouldLogAccountDebited() {
        AuditEvent event = new AuditEvent("ACCOUNT_DEBITED", "Amount withdrawn from account", LocalDateTime.now());

        eventListener.onAuditEvent(event);

        verify(auditLogger).log(eq(AuditAction.ACCOUNT_DEBITED), eq("Amount withdrawn from account"));
    }

    @Test
    void shouldLogAccountCredited() {
        AuditEvent event = new AuditEvent("ACCOUNT_CREDITED", "Amount deposited to account", LocalDateTime.now());

        eventListener.onAuditEvent(event);

        verify(auditLogger).log(eq(AuditAction.ACCOUNT_CREDITED), eq("Amount deposited to account"));
    }

    @Test
    void shouldLogAccountSuspended() {
        AuditEvent event = new AuditEvent("ACCOUNT_SUSPENDED", "Hesap donduruldu", LocalDateTime.now());

        eventListener.onAuditEvent(event);

        verify(auditLogger).log(eq(AuditAction.ACCOUNT_SUSPENDED), eq("Hesap donduruldu"));
    }

    @Test
    void shouldLogAccountClosed() {
        AuditEvent event = new AuditEvent("ACCOUNT_CLOSED", "Hesap kapatıldı", LocalDateTime.now());

        eventListener.onAuditEvent(event);

        verify(auditLogger).log(eq(AuditAction.ACCOUNT_CLOSED), eq("Hesap kapatıldı"));
    }
}
