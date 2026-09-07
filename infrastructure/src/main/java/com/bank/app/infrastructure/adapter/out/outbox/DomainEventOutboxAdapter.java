package com.bank.app.infrastructure.adapter.out.outbox;

import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.application.port.out.OutboxPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.domain.event.DomainEvent;
import com.bank.app.infrastructure.adapter.in.config.OutboxProperties;
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
    private static final int DEFAULT_PARTITION_MODULO = 16;

    private final OutboxPort outboxPort;
    private final ObjectMapper objectMapper;
    private final ClockProviderPort clockProvider;
    private final OutboxProperties outboxProperties;

    public DomainEventOutboxAdapter(OutboxPort outboxPort, ObjectMapper objectMapper,
            ClockProviderPort clockProvider, OutboxProperties outboxProperties) {
        this.outboxPort = outboxPort;
        this.objectMapper = objectMapper;
        this.clockProvider = clockProvider;
        this.outboxProperties = outboxProperties;
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
        LocalDateTime now = LocalDateTime.now(clockProvider.clock());
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

    private int resolvePartition(String aggregateId) {
        if (aggregateId == null) {
            return 0;
        }
        int modulo = outboxProperties.partitionCount() > 0
                ? outboxProperties.partitionCount()
                : DEFAULT_PARTITION_MODULO;
        return Math.abs(aggregateId.hashCode() % modulo);
    }
}
