package com.bank.app.user.adapter.out.outbox;

import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.common.application.port.out.OutboxEventPort;
import com.bank.app.common.application.port.out.OutboxPort.EventEntry;
import com.bank.app.user.domain.UserRegisteredEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class UserEventOutboxHandler implements OutboxEventPort {

    private static final Logger log = LoggerFactory.getLogger(UserEventOutboxHandler.class);
    private static final String DEDUP_KEY_PREFIX = "outbox_handler_UserEventOutboxHandler_";

    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final IdempotencyPort idempotencyPort;

    public UserEventOutboxHandler(ObjectMapper objectMapper,
                                   ApplicationEventPublisher eventPublisher,
                                   IdempotencyPort idempotencyPort) {
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.idempotencyPort = idempotencyPort;
    }

    @Override
    public boolean supports(String eventType) {
        return "UserRegisteredEvent".equals(eventType);
    }

    @Override
    public void handle(EventEntry event) {
        String dedupKey = DEDUP_KEY_PREFIX + event.id();
        if (!idempotencyPort.tryCreate(dedupKey, LocalDateTime.now())) {
            log.info("Duplicate outbox event detected, skipping. handler=UserEventOutboxHandler, eventId={}", event.id());
            return;
        }

        try {
            UserRegisteredEvent userEvent = objectMapper.readValue(
                    event.payload(), UserRegisteredEvent.class);
            eventPublisher.publishEvent(userEvent);
            log.debug("Published UserRegisteredEvent: username={}, id={}",
                    userEvent.username(), event.id());
        } catch (Exception e) {
            log.error("Failed to handle UserRegisteredEvent: id={}", event.id(), e);
            throw new RuntimeException("UserEventOutboxHandler failed", e);
        }
    }
}
