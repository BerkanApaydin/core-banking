package com.bank.app.infrastructure.adapter.out.outbox;

import com.bank.app.account.domain.AccountClosedEvent;
import com.bank.app.account.domain.AccountCreatedEvent;
import com.bank.app.account.domain.AccountCreditedEvent;
import com.bank.app.account.domain.AccountDebitedEvent;
import com.bank.app.account.domain.AccountSuspendedEvent;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.application.port.out.OutboxPort;
import com.bank.app.common.domain.event.DomainEvent;
import com.bank.app.infrastructure.adapter.out.event.SpringEventPublisherAdapter;
import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import com.bank.app.transfer.domain.TransferCancelledEvent;
import com.bank.app.transfer.domain.TransferCompletedEvent;
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

    private final SpringEventPublisherAdapter springAdapter;
    private final OutboxPort outboxPort;
    private final ObjectMapper objectMapper;

    public DomainEventOutboxAdapter(SpringEventPublisherAdapter springAdapter,
                                    OutboxPort outboxPort, ObjectMapper objectMapper) {
        this.springAdapter = springAdapter;
        this.outboxPort = outboxPort;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(DomainEvent event) {
        springAdapter.publish(event);
        persistToOutbox(event);
    }

    private void persistToOutbox(DomainEvent event) {
        try {
            OutboxPort.EventEntry entry = toOutboxEntry(event);
            outboxPort.save(entry);
            log.debug("Stored outbox event: type={}, aggregateType={}, id={}",
                    entry.eventType(), entry.aggregateType(), entry.id());
        } catch (Exception e) {
            log.error("Failed to store outbox event: {}", e.getMessage(), e);
        }
    }

    private OutboxPort.EventEntry toOutboxEntry(DomainEvent event) {
        String payload = serialize(event);
        String eventType = event.getClass().getSimpleName();
        LocalDateTime now = LocalDateTime.now();
        String id = UUID.randomUUID().toString();

        String aggregateType = resolveAggregateType(event);
        String aggregateId = resolveAggregateId(event);
        int partition = resolvePartition(aggregateId);

        return new OutboxPort.EventEntry(
                id, aggregateType, aggregateId, eventType, payload,
                0, false, false, null, partition, now
        );
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("Failed to serialize domain event: {}", event.getClass().getSimpleName(), e);
            return "{}";
        }
    }

    private static String resolveAggregateType(DomainEvent event) {
        return switch (event) {
            case AccountCreatedEvent ignored -> "Account";
            case AccountDebitedEvent ignored -> "Account";
            case AccountCreditedEvent ignored -> "Account";
            case AccountClosedEvent ignored -> "Account";
            case AccountSuspendedEvent ignored -> "Account";
            case TransferCompletedEvent ignored -> "Transfer";
            case TransferCancelledEvent ignored -> "Transfer";
            case AsyncTransferCompletedEvent ignored -> "Transfer";
            default -> event.getClass().getSimpleName();
        };
    }

    private static String resolveAggregateId(DomainEvent event) {
        return switch (event) {
            case AccountCreatedEvent e -> String.valueOf(e.accountId());
            case AccountDebitedEvent e -> String.valueOf(e.accountId());
            case AccountCreditedEvent e -> String.valueOf(e.accountId());
            case AccountClosedEvent e -> String.valueOf(e.accountId());
            case AccountSuspendedEvent e -> String.valueOf(e.accountId());
            case TransferCompletedEvent e -> String.valueOf(e.transferId());
            case TransferCancelledEvent e -> String.valueOf(e.transferId());
            case AsyncTransferCompletedEvent e -> String.valueOf(e.transferId());
            default -> "unknown";
        };
    }

    private static int resolvePartition(String aggregateId) {
        return Math.abs(aggregateId.hashCode() % 16);
    }
}
