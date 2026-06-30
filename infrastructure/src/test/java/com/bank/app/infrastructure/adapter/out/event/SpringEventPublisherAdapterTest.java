package com.bank.app.infrastructure.adapter.out.event;

import com.bank.app.common.domain.event.DomainEvent;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class SpringEventPublisherAdapterTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SpringEventPublisherAdapter adapter;

    @Test
    void shouldPublishEvent() {
        DomainEvent event = new DomainEvent() {
            @Override
            public LocalDateTime occurredAt() {
                return LocalDateTime.now();
            }
        };

        adapter.publish(event);

        verify(eventPublisher).publishEvent(event);
    }
}
