package com.bank.app.user.adapter.out.outbox;

import com.bank.app.common.application.port.out.OutboxEventPort;
import com.bank.app.common.application.port.out.OutboxPort.EventEntry;
import com.bank.app.user.domain.UserRegisteredEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class UserEventOutboxHandler implements OutboxEventPort {

    private static final Logger log = LoggerFactory.getLogger(UserEventOutboxHandler.class);

    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public UserEventOutboxHandler(ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher) {
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public boolean supports(String eventType) {
        return "UserRegisteredEvent".equals(eventType);
    }

    @Override
    public void handle(EventEntry event) {
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
