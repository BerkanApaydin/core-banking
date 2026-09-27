package com.bank.app.infrastructure.adapter.out.event;

import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.audit.application.port.out.SaveAuditLogPort;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.audit.domain.AuditLog;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditEventPublisherAdapterTest {
 
     @Mock
     private SaveAuditLogPort saveAuditLogPort;
 
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
 }
