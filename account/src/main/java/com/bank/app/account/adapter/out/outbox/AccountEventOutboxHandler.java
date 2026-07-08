package com.bank.app.account.adapter.out.outbox;

import com.bank.app.account.domain.AccountClosedEvent;
import com.bank.app.account.domain.AccountCreatedEvent;
import com.bank.app.account.domain.AccountCreditedEvent;
import com.bank.app.account.domain.AccountDebitedEvent;
import com.bank.app.account.domain.AccountSuspendedEvent;
import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.common.application.port.out.OutboxEventPort;
import com.bank.app.common.application.port.out.OutboxPort.EventEntry;
import com.bank.app.common.domain.event.DomainEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class AccountEventOutboxHandler implements OutboxEventPort {

    private static final Logger log = LoggerFactory.getLogger(AccountEventOutboxHandler.class);
    private static final String DEDUP_KEY_PREFIX = "outbox_handler_AccountEventOutboxHandler_";

    private static final Map<String, Class<? extends DomainEvent>> SUPPORTED_EVENTS = Map.of(
            "AccountCreatedEvent", AccountCreatedEvent.class,
            "AccountDebitedEvent", AccountDebitedEvent.class,
            "AccountCreditedEvent", AccountCreditedEvent.class,
            "AccountSuspendedEvent", AccountSuspendedEvent.class,
            "AccountClosedEvent", AccountClosedEvent.class
    );

    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final IdempotencyPort idempotencyPort;

    public AccountEventOutboxHandler(ObjectMapper objectMapper,
                                      ApplicationEventPublisher eventPublisher,
                                      IdempotencyPort idempotencyPort) {
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.idempotencyPort = idempotencyPort;
    }

    @Override
    public boolean supports(String eventType) {
        return eventType != null && SUPPORTED_EVENTS.containsKey(eventType);
    }

    @Override
    public void handle(EventEntry event) {
        String dedupKey = DEDUP_KEY_PREFIX + event.id();
        if (!idempotencyPort.tryCreate(dedupKey, LocalDateTime.now())) {
            log.info("Duplicate outbox event detected, skipping. handler=AccountEventOutboxHandler, eventId={}", event.id());
            return;
        }

        Class<? extends DomainEvent> eventClass = SUPPORTED_EVENTS.get(event.eventType());
        if (eventClass == null) {
            log.warn("Unsupported account event type: {}", event.eventType());
            return;
        }
        try {
            DomainEvent domainEvent = objectMapper.readValue(event.payload(), eventClass);
            eventPublisher.publishEvent(domainEvent);
            log.debug("Published account event: type={}, id={}", event.eventType(), event.id());
        } catch (Exception e) {
            log.error("Failed to handle account event: type={}, id={}", event.eventType(), event.id(), e);
            throw new RuntimeException("AccountEventOutboxHandler failed for " + event.eventType(), e);
        }
    }
}
