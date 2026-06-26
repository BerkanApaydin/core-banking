package com.bank.app.common.domain.event;

import java.util.List;

public interface DomainEventProvider {
    List<DomainEvent> getDomainEvents();
    void clearDomainEvents();
}
