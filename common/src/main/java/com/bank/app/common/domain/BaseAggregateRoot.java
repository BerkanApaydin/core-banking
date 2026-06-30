package com.bank.app.common.domain;

import com.bank.app.common.domain.event.DomainEvent;
import com.bank.app.common.domain.event.DomainEventProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class BaseAggregateRoot implements DomainEventProvider {

    private final transient List<DomainEvent> domainEvents = new ArrayList<>();

    @Override
    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    @Override
    public void clearDomainEvents() {
        domainEvents.clear();
    }

    protected void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }
}
