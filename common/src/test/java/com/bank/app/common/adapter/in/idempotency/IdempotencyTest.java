package com.bank.app.common.adapter.in.idempotency;

import com.bank.app.common.adapter.in.config.IdempotencyProperties;
import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.common.application.port.out.IdempotencyPort.Entry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class IdempotencyTest {

    @Mock
    private IdempotencyPort idempotencyPort;

    private IdempotencyCleanupScheduler scheduler;
    private IdempotencyGuard manager;

    private final IdempotencyProperties idempotencyProperties = new IdempotencyProperties(24, "0 0 * * * *");

    @BeforeEach
    void setUp() {
        scheduler = new IdempotencyCleanupScheduler(idempotencyPort, idempotencyProperties);
        manager = new IdempotencyGuard(idempotencyPort);
    }

    @Test
    void shouldDeleteExpiredKeysOnCleanup() {
        when(idempotencyPort.deleteExpired(any(LocalDateTime.class))).thenReturn(5);

        scheduler.cleanupExpiredKeys();

        verify(idempotencyPort).deleteExpired(any(LocalDateTime.class));
    }

    @Test
    void shouldReturnNewStatusWhenKeyDoesNotExist() {
        when(idempotencyPort.findById("key-1")).thenReturn(Optional.empty());
        when(idempotencyPort.tryCreate(eq("key-1"), any(LocalDateTime.class))).thenReturn(true);

        IdempotencyGuard.IdempotencyResult result = manager.startRequest("key-1");

        assertEquals(IdempotencyGuard.IdempotencyResult.Status.NEW, result.status());
        assertNull(result.responseBody());
        assertFalse(result.isCompleted());
        assertFalse(result.isPending());
    }

    @Test
    void shouldReturnPendingStatusWhenKeyExistsAndPending() {
        Entry entry = new Entry("key-1", "PENDING", null, null, LocalDateTime.now());
        when(idempotencyPort.findById("key-1")).thenReturn(Optional.of(entry));

        IdempotencyGuard.IdempotencyResult result = manager.startRequest("key-1");

        assertEquals(IdempotencyGuard.IdempotencyResult.Status.PENDING, result.status());
        assertTrue(result.isPending());
        assertFalse(result.isCompleted());
    }

    @Test
    void shouldReturnCompletedStatusWhenKeyExistsAndCompleted() {
        Entry entry = new Entry("key-1", "COMPLETED", "resp-body", 200, LocalDateTime.now());
        when(idempotencyPort.findById("key-1")).thenReturn(Optional.of(entry));

        IdempotencyGuard.IdempotencyResult result = manager.startRequest("key-1");

        assertEquals(IdempotencyGuard.IdempotencyResult.Status.COMPLETED, result.status());
        assertEquals("resp-body", result.responseBody());
        assertEquals(200, result.responseStatus());
        assertTrue(result.isCompleted());
        assertFalse(result.isPending());
    }

    @Test
    void shouldDeleteFailedKeyAndReturnNewWhenKeyExistsAndFailed() {
        Entry entry = new Entry("key-1", "FAILED", null, null, LocalDateTime.now());
        when(idempotencyPort.findById("key-1")).thenReturn(Optional.of(entry));

        IdempotencyGuard.IdempotencyResult result = manager.startRequest("key-1");

        assertEquals(IdempotencyGuard.IdempotencyResult.Status.NEW, result.status());
        verify(idempotencyPort).deleteById("key-1");
    }

    @Test
    void shouldResolveConflictAsPendingWhenSaveFailsAndKeyIsPending() {
        Entry existing = new Entry("key-1", "PENDING", null, null, LocalDateTime.now());
        when(idempotencyPort.findById("key-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(idempotencyPort.tryCreate(eq("key-1"), any(LocalDateTime.class))).thenReturn(false);

        IdempotencyGuard.IdempotencyResult result = manager.startRequest("key-1");

        assertEquals(IdempotencyGuard.IdempotencyResult.Status.PENDING, result.status());
    }

    @Test
    void shouldResolveConflictAsCompletedWhenSaveFailsAndKeyIsCompleted() {
        Entry existing = new Entry("key-1", "COMPLETED", "resp", 200, LocalDateTime.now());
        when(idempotencyPort.findById("key-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(idempotencyPort.tryCreate(eq("key-1"), any(LocalDateTime.class))).thenReturn(false);

        IdempotencyGuard.IdempotencyResult result = manager.startRequest("key-1");

        assertEquals(IdempotencyGuard.IdempotencyResult.Status.COMPLETED, result.status());
        assertEquals("resp", result.responseBody());
    }

    @Test
    void shouldResolveConflictAsPendingWhenSaveFailsAndKeyNotFound() {
        when(idempotencyPort.findById("key-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(idempotencyPort.tryCreate(eq("key-1"), any(LocalDateTime.class))).thenReturn(false);

        IdempotencyGuard.IdempotencyResult result = manager.startRequest("key-1");

        assertEquals(IdempotencyGuard.IdempotencyResult.Status.PENDING, result.status());
    }

    @Test
    void shouldCompleteRequestAndUpdateEntity() {
        manager.completeRequest("key-1", "success-response", 201);

        verify(idempotencyPort).markCompleted("key-1", "success-response", 201);
    }

    @Test
    void shouldMarkKeyAsFailedOnFailRequest() {
        manager.failRequest("key-1");

        verify(idempotencyPort).markFailed("key-1");
    }

    @Test
    void shouldDoNothingWhenFailRequestForNonExistentKey() {
        manager.failRequest("key-1");

        verify(idempotencyPort).markFailed("key-1");
    }

    @Test
    void shouldThrowNullPointerExceptionWhenStartRequestWithNullKey() {
        assertThrows(NullPointerException.class, () -> manager.startRequest(null));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenCompleteRequestWithNullKey() {
        assertThrows(NullPointerException.class, () -> manager.completeRequest(null, "body", 200));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenFailRequestWithNullKey() {
        assertThrows(NullPointerException.class, () -> manager.failRequest(null));
    }

    @Test
    void shouldDoNothingWhenCompleteRequestForNonExistentKey() {
        manager.completeRequest("nonexistent", "body", 200);

        verify(idempotencyPort).markCompleted("nonexistent", "body", 200);
    }
}
