package com.bank.app.infrastructure.adapter.out.outbox;

import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.application.port.out.OutboxPort;
import com.bank.app.common.domain.event.DomainEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Primary
public class DomainEventOutboxAdapter implements EventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(DomainEventOutboxAdapter.class);

    private final OutboxPort outboxPort;
    private final ObjectMapper objectMapper;

    public DomainEventOutboxAdapter(OutboxPort outboxPort, ObjectMapper objectMapper) {
        this.outboxPort = outboxPort;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(DomainEvent event) {
        OutboxPort.EventEntry entry = toOutboxEntry(event);
        outboxPort.save(entry);
        log.debug("Stored outbox event: type={}, aggregateType={}, id={}",
                entry.eventType(), entry.aggregateType(), entry.id());
    }

    private OutboxPort.EventEntry toOutboxEntry(DomainEvent event) {
        String payload = serialize(event);
        String eventType = event.getClass().getSimpleName();
        LocalDateTime now = LocalDateTime.now();
        String id = UUID.randomUUID().toString();

        String aggregateType = event.aggregateType();
        String aggregateId = event.aggregateId();
        int partition = resolvePartition(aggregateId);

        return new OutboxPort.EventEntry(
                id, aggregateType, aggregateId, eventType, payload,
                0, false, false, null, partition, now
        );
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize domain event: {}", event.getClass().getSimpleName(), e);
            throw new RuntimeException("Failed to serialize domain event: " + event.getClass().getSimpleName(), e);
        }
    }

    private static int resolvePartition(String aggregateId) {
        if (aggregateId == null) {
            return 0;
        }
        return Math.abs(aggregateId.hashCode() % 16);
    }
}
