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
        AuditEvent event = new AuditEvent("ACCOUNT_CREATED", "Yeni hesap oluşturuldu", LocalDateTime.now());

        eventListener.onAuditEvent(event);

        verify(auditLogger).log(eq(AuditAction.ACCOUNT_CREATED), eq("Yeni hesap oluşturuldu"));
    }

    @Test
    void shouldLogTransferExecuted() {
        AuditEvent event = new AuditEvent("TRANSFER_EXECUTED", "Para transferi gerçekleştirildi", LocalDateTime.now());

        eventListener.onAuditEvent(event);

        verify(auditLogger).log(eq(AuditAction.TRANSFER_EXECUTED), eq("Para transferi gerçekleştirildi"));
    }

    @Test
    void shouldLogTransferCancelled() {
        AuditEvent event = new AuditEvent("TRANSFER_CANCELLED", "Transfer iptal edildi", LocalDateTime.now());

        eventListener.onAuditEvent(event);

        verify(auditLogger).log(eq(AuditAction.TRANSFER_CANCELLED), eq("Transfer iptal edildi"));
    }

    @Test
    void shouldLogAccountDebited() {
        AuditEvent event = new AuditEvent("ACCOUNT_DEBITED", "Hesaptan para çekildi", LocalDateTime.now());

        eventListener.onAuditEvent(event);

        verify(auditLogger).log(eq(AuditAction.ACCOUNT_DEBITED), eq("Hesaptan para çekildi"));
    }

    @Test
    void shouldLogAccountCredited() {
        AuditEvent event = new AuditEvent("ACCOUNT_CREDITED", "Hesaba para yatırıldı", LocalDateTime.now());

        eventListener.onAuditEvent(event);

        verify(auditLogger).log(eq(AuditAction.ACCOUNT_CREDITED), eq("Hesaba para yatırıldı"));
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
