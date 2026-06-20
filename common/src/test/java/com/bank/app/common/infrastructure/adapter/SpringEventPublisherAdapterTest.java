package com.bank.app.common.infrastructure.adapter;

import com.bank.app.common.application.port.out.EventPublisherPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpringEventPublisherAdapterTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SpringEventPublisherAdapter adapter;

    @Test
    void shouldPublishEvent() {
        Object event = new Object();

        adapter.publish(event);

        verify(eventPublisher).publishEvent(event);
    }
}
