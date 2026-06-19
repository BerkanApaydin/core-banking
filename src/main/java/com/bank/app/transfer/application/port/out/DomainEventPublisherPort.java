package com.bank.app.transfer.application.port.out;

public interface DomainEventPublisherPort {
    void publish(Object event);
}
