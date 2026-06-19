package com.bank.app.common.application.port.out;

public interface EventPublisherPort {
    void publish(Object event);
}
