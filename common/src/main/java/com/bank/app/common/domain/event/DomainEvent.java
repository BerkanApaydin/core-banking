package com.bank.app.common.domain.event;

import java.time.LocalDateTime;

public interface DomainEvent {
    LocalDateTime occurredAt();
    String aggregateType();
    String aggregateId();
}
