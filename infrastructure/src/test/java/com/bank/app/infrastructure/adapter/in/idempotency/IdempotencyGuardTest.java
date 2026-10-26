package com.bank.app.infrastructure.adapter.in.idempotency;

import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.common.application.port.out.IdempotencyPort.Entry;
import com.bank.app.common.domain.exception.ConcurrentRequestException;
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
        @DisplayName("should atomically reserve a FAILED key before returning NEW")
        void shouldReserveFailedAndReturnNew() {
            Entry existing = new Entry("key-1", "FAILED", null, null, LocalDateTime.now());
            when(idempotencyPort.findById("key-1")).thenReturn(Optional.of(existing));
            when(idempotencyPort.tryResetFailed(eq("key-1"), any(LocalDateTime.class))).thenReturn(true);

            IdempotencyResult result = guard.startRequest("key-1");

            assertEquals(IdempotencyResult.Status.NEW, result.status());
            verify(idempotencyPort).tryResetFailed(eq("key-1"), any(LocalDateTime.class));
        }

        @Test
        void shouldNotRunFailedRetryWhenAnotherRequestClaimsItFirst() {
            Entry failed = new Entry("key-1", "FAILED", null, null, LocalDateTime.now());
            Entry pending = new Entry("key-1", "PENDING", null, null, LocalDateTime.now());
            when(idempotencyPort.findById("key-1"))
                    .thenReturn(Optional.of(failed))
                    .thenReturn(Optional.of(pending));
            when(idempotencyPort.tryResetFailed(eq("key-1"), any(LocalDateTime.class))).thenReturn(false);

            assertEquals(IdempotencyResult.Status.PENDING, guard.startRequest("key-1").status());
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
        @DisplayName("should preserve non-replayable flag for an oversized stored response")
        void shouldPreserveNonReplayableFlag() {
            Entry existing = new Entry("key-1", "COMPLETED", null, 201,
                    LocalDateTime.now(), null, false);
            when(idempotencyPort.findById("key-1")).thenReturn(Optional.of(existing));

            IdempotencyResult result = guard.startRequest("key-1");

            assertEquals(IdempotencyResult.Status.COMPLETED, result.status());
            assertFalse(result.replayable());
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

        @Test
        @DisplayName("should fall back to the system clock when no clock provider is supplied")
        void shouldFallBackToSystemClock() {
            IdempotencyGuard guardWithNullClock = new IdempotencyGuard(idempotencyPort, null);
            when(idempotencyPort.findById("key-clock")).thenReturn(Optional.empty());
            when(idempotencyPort.tryCreate(eq("key-clock"), any(LocalDateTime.class))).thenReturn(true);

            assertEquals(IdempotencyResult.Status.NEW,
                    guardWithNullClock.startRequest("key-clock").status());
        }

        @Test
        @DisplayName("should reject a reused key with a different request hash")
        void shouldRejectDifferentRequestHash() {
            Entry existing = new Entry("key-1", "PENDING", null, null,
                    LocalDateTime.now(), "hash-a", true);
            when(idempotencyPort.findById("key-1")).thenReturn(Optional.of(existing));

            assertThrows(ConcurrentRequestException.class,
                    () -> guard.startRequest("key-1", "hash-b"));
        }

        @Test
        @DisplayName("should fail closed on an unknown persisted status")
        void shouldRejectUnknownPersistedStatus() {
            // A corrupt/manually-edited row must never be treated as NEW
            // (which would re-execute a money movement under a reused key).
            Entry corrupt = new Entry("key-1", "CORRUPT", null, null, LocalDateTime.now());
            when(idempotencyPort.findById("key-1")).thenReturn(Optional.of(corrupt));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> guard.startRequest("key-1"));

            assertTrue(ex.getMessage().contains("CORRUPT"));
            verify(idempotencyPort, never()).tryCreate(anyString(), any());
        }

        @Test
        @DisplayName("should replay the winner when a FAILED claim loses to a completed request")
        void shouldReplayCompletedWinnerAfterFailedClaimLoses() {
            Entry failed = new Entry("key-1", "FAILED", null, null, LocalDateTime.now());
            Entry completed = new Entry("key-1", "COMPLETED", "winner", 200,
                    LocalDateTime.now(), null, true);
            when(idempotencyPort.findById("key-1"))
                    .thenReturn(Optional.of(failed))
                    .thenReturn(Optional.of(completed));
            when(idempotencyPort.tryResetFailed(eq("key-1"), any(LocalDateTime.class)))
                    .thenReturn(false);

            IdempotencyResult result = guard.startRequest("key-1");

            assertEquals(IdempotencyResult.Status.COMPLETED, result.status());
            assertEquals("winner", result.responseBody());
        }
    }

    @Test
    @DisplayName("three-argument result constructor derives replayability from status")
    void shouldDeriveReplayabilityFromStatus() {
        assertTrue(new IdempotencyResult(IdempotencyResult.Status.COMPLETED, "body", 200)
                .replayable());
        assertFalse(new IdempotencyResult(IdempotencyResult.Status.PENDING, null, null)
                .replayable());
        assertFalse(new IdempotencyResult(IdempotencyResult.Status.NEW, null, null)
                .replayable());
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
