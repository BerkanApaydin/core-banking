package com.bank.app.user.adapter.out.outbox;

import com.bank.app.common.application.port.out.OutboxPort;
import com.bank.app.user.domain.UserRegisteredEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserEventOutboxHandler")
class UserEventOutboxHandlerTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ObjectMapper objectMapper;
    private UserEventOutboxHandler handler;

    @Captor
    private ArgumentCaptor<UserRegisteredEvent> eventCaptor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.findAndRegisterModules();
        handler = new UserEventOutboxHandler(objectMapper, eventPublisher);
    }

    @Nested
    @DisplayName("supports")
    class Supports {

        @Test
        @DisplayName("should return true for UserRegisteredEvent")
        void shouldReturnTrueForUserRegisteredEvent() {
            assertTrue(handler.supports("UserRegisteredEvent"));
        }

        @Test
        @DisplayName("should return false for unknown event type")
        void shouldReturnFalseForUnknown() {
            assertFalse(handler.supports("UnknownEvent"));
            assertFalse(handler.supports(""));
            assertFalse(handler.supports(null));
        }
    }

    @Nested
    @DisplayName("handle")
    class Handle {

        @Test
        @DisplayName("should publish UserRegisteredEvent on success")
        void shouldPublishEventSuccessfully() throws Exception {
            String json = objectMapper.writeValueAsString(new UserRegisteredEvent(
                    "1", "testuser", "ROLE_USER", LocalDateTime.now()));
            OutboxPort.EventEntry event = new OutboxPort.EventEntry("evt-1", "User",
                    "1", "UserRegisteredEvent", json, 0, false, false, null, 0, LocalDateTime.now());

            handler.handle(event);

            verify(eventPublisher).publishEvent(eventCaptor.capture());
            UserRegisteredEvent captured = eventCaptor.getValue();
            assertEquals("1", captured.userId());
            assertEquals("testuser", captured.username());
            assertEquals("ROLE_USER", captured.role());
        }

        @Test
        @DisplayName("should throw RuntimeException when payload is malformed")
        void shouldThrowOnMalformedPayload() {
            OutboxPort.EventEntry event = new OutboxPort.EventEntry("evt-2", "User",
                    "1", "UserRegisteredEvent", "{invalid json}", 0, false, false, null, 0, LocalDateTime.now());

            RuntimeException ex = assertThrows(RuntimeException.class, () -> handler.handle(event));
            assertTrue(ex.getMessage().contains("UserEventOutboxHandler failed"));
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("should throw RuntimeException when payload is null")
        void shouldThrowOnNullPayload() {
            OutboxPort.EventEntry event = new OutboxPort.EventEntry("evt-3", "User",
                    "1", "UserRegisteredEvent", null, 0, false, false, null, 0, LocalDateTime.now());

            assertThrows(RuntimeException.class, () -> handler.handle(event));
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("should throw RuntimeException when publisher fails")
        void shouldThrowWhenPublisherFails() throws Exception {
            String json = objectMapper.writeValueAsString(new UserRegisteredEvent(
                    "1", "testuser", "ROLE_USER", LocalDateTime.now()));
            OutboxPort.EventEntry event = new OutboxPort.EventEntry("evt-4", "User",
                    "1", "UserRegisteredEvent", json, 0, false, false, null, 0, LocalDateTime.now());

            doThrow(new RuntimeException("publisher error")).when(eventPublisher).publishEvent(any());

            RuntimeException ex = assertThrows(RuntimeException.class, () -> handler.handle(event));
            assertTrue(ex.getMessage().contains("UserEventOutboxHandler failed"));
        }
    }
}
