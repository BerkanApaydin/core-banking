package com.bank.app.common.application.port.out;

import org.springframework.lang.NonNull;

import com.bank.app.common.domain.event.DomainEvent;

public interface EventPublisherPort {
    void publish(@NonNull DomainEvent event);
}
