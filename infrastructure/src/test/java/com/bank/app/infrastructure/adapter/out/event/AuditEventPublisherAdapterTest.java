package com.bank.app.infrastructure.adapter.out.event;

import com.bank.app.common.domain.event.AuditEvent;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditEventPublisherAdapterTest {
 
     @Mock
     private ApplicationEventPublisher eventPublisher;
 
     @InjectMocks
     private AuditEventPublisherAdapter adapter;
 
     @Test
     void shouldPublishAuditEvent() {
         AuditEvent event = new AuditEvent("TEST_EVENT", "test detail", LocalDateTime.now());
 
         adapter.publish(event);
 
         verify(eventPublisher).publishEvent(event);
     }
 }
