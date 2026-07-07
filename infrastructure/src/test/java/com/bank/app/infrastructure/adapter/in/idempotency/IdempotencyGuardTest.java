package com.bank.app.infrastructure.adapter.in.idempotency;

import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.common.application.port.out.IdempotencyPort.Entry;
import com.bank.app.infrastructure.adapter.in.idempotency.IdempotencyGuard.IdempotencyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyGuard")
class IdempotencyGuardTest {

    @Mock
    private IdempotencyPort idempotencyPort;

    private IdempotencyGuard guard;

    @BeforeEach
    void setUp() {
        guard = new IdempotencyGuard(idempotencyPort);
    }

    @Nested
    @DisplayName("startRequest")
    class StartRequest {

        @Test
        @DisplayName("should return NEW when key does not exist and tryCreate succeeds")
        void shouldReturnNewForNewKey() {
            when(idempotencyPort.findById("key-1")).thenReturn(Optional.empty());
            when(idempotencyPort.tryCreate(eq("key-1"), any(LocalDateTime.class))).thenReturn(true);

            IdempotencyResult result = guard.startRequest("key-1");

            assertEquals(IdempotencyResult.Status.NEW, result.status());
            assertNull(result.responseBody());
            assertNull(result.responseStatus());
            assertFalse(result.isCompleted());
            assertFalse(result.isPending());
        }

        @Test
        @DisplayName("should return PENDING when key exists with PENDING status")
        void shouldReturnPendingForExistingPendingKey() {
            Entry existing = new Entry("key-1", "PENDING", null, null, LocalDateTime.now());
            when(idempotencyPort.findById("key-1")).thenReturn(Optional.of(existing));

            IdempotencyResult result = guard.startRequest("key-1");

            assertEquals(IdempotencyResult.Status.PENDING, result.status());
            assertTrue(result.isPending());
            verify(idempotencyPort, never()).tryCreate(anyString(), any());
        }

        @Test
        @DisplayName("should delete and return NEW when key exists with FAILED status")
        void shouldDeleteFailedAndReturnNew() {
            Entry existing = new Entry("key-1", "FAILED", null, null, LocalDateTime.now());
            when(idempotencyPort.findById("key-1")).thenReturn(Optional.of(existing));

            IdempotencyResult result = guard.startRequest("key-1");

            assertEquals(IdempotencyResult.Status.NEW, result.status());
            verify(idempotencyPort).deleteById("key-1");
        }

        @Test
        @DisplayName("should return COMPLETED when key exists with COMPLETED status")
        void shouldReturnCompletedForExistingCompletedKey() {
            Entry existing = new Entry("key-1", "COMPLETED", "{\"status\":\"ok\"}", 200, LocalDateTime.now());
            when(idempotencyPort.findById("key-1")).thenReturn(Optional.of(existing));

            IdempotencyResult result = guard.startRequest("key-1");

            assertEquals(IdempotencyResult.Status.COMPLETED, result.status());
            assertTrue(result.isCompleted());
            assertEquals("{\"status\":\"ok\"}", result.responseBody());
            assertEquals(200, result.responseStatus());
            verify(idempotencyPort, never()).tryCreate(anyString(), any());
        }

        @Test
        @DisplayName("should return PENDING on race condition when tryCreate fails and key appears as PENDING")
        void shouldReturnPendingOnRaceCondition() {
            when(idempotencyPort.findById("key-1"))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(new Entry("key-1", "PENDING", null, null, LocalDateTime.now())));
            when(idempotencyPort.tryCreate(eq("key-1"), any(LocalDateTime.class))).thenReturn(false);

            IdempotencyResult result = guard.startRequest("key-1");

            assertEquals(IdempotencyResult.Status.PENDING, result.status());
            assertTrue(result.isPending());
        }

        @Test
        @DisplayName("should return COMPLETED on race condition when tryCreate fails and key appears as COMPLETED")
        void shouldReturnCompletedOnRaceCondition() {
            when(idempotencyPort.findById("key-1"))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(new Entry("key-1", "COMPLETED", "ok", 200, LocalDateTime.now())));
            when(idempotencyPort.tryCreate(eq("key-1"), any(LocalDateTime.class))).thenReturn(false);

            IdempotencyResult result = guard.startRequest("key-1");

            assertEquals(IdempotencyResult.Status.COMPLETED, result.status());
            assertTrue(result.isCompleted());
            assertEquals("ok", result.responseBody());
            assertEquals(200, result.responseStatus());
        }

        @Test
        @DisplayName("should return PENDING on race condition when tryCreate fails and key disappears")
        void shouldReturnPendingOnRaceConditionWhenKeyDisappears() {
            when(idempotencyPort.findById("key-1"))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.empty());
            when(idempotencyPort.tryCreate(eq("key-1"), any(LocalDateTime.class))).thenReturn(false);

            IdempotencyResult result = guard.startRequest("key-1");

            assertEquals(IdempotencyResult.Status.PENDING, result.status());
        }

        @Test
        @DisplayName("should throw NullPointerException when key is null")
        void shouldThrowOnNullKey() {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> guard.startRequest(null));
            assertEquals("Idempotency key must not be null", ex.getMessage());
        }
    }

    @Nested
    @DisplayName("completeRequest")
    class CompleteRequest {

        @Test
        @DisplayName("should mark key as completed with response")
        void shouldCompleteRequest() {
            guard.completeRequest("key-1", "{\"id\":1}", 201);

            verify(idempotencyPort).markCompleted("key-1", "{\"id\":1}", 201);
        }

        @Test
        @DisplayName("should throw NullPointerException when key is null")
        void shouldThrowOnNullKey() {
            assertThrows(NullPointerException.class,
                    () -> guard.completeRequest(null, "body", 200));
        }
    }

    @Nested
    @DisplayName("failRequest")
    class FailRequest {

        @Test
        @DisplayName("should mark key as failed")
        void shouldFailRequest() {
            guard.failRequest("key-1");

            verify(idempotencyPort).markFailed("key-1");
        }

        @Test
        @DisplayName("should throw NullPointerException when key is null")
        void shouldThrowOnNullKey() {
            assertThrows(NullPointerException.class,
                    () -> guard.failRequest(null));
        }
    }
}
