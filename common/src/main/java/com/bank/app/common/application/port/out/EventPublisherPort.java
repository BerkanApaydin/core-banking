package com.bank.app.common.application.port.out;

import com.bank.app.common.domain.event.DomainEvent;

public interface EventPublisherPort {
    void publish(DomainEvent event);
}
