package com.bank.app.common.idempotency;

import com.bank.app.common.persistence.IdempotencyKeyJpaEntity;
import com.bank.app.common.persistence.SpringDataIdempotencyKeyRepo;
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
    private SpringDataIdempotencyKeyRepo repo;

    private IdempotencyCleanupScheduler scheduler;
    private IdempotencyManager manager;

    @BeforeEach
    void setUp() {
        scheduler = new IdempotencyCleanupScheduler(repo, 24);
        manager = new IdempotencyManager(repo);
    }

    @Test
    void shouldDeleteExpiredKeysOnCleanup() {
        when(repo.deleteByCreatedAtBefore(any(LocalDateTime.class))).thenReturn(5);

        scheduler.cleanupExpiredKeys();

        verify(repo).deleteByCreatedAtBefore(any(LocalDateTime.class));
    }

    @Test
    void shouldReturnNewStatusWhenKeyDoesNotExist() {
        when(repo.findById("key-1")).thenReturn(Optional.empty());

        IdempotencyManager.IdempotencyResult result = manager.startRequest("key-1");

        assertEquals(IdempotencyManager.IdempotencyResult.Status.NEW, result.status());
        assertNull(result.responseBody());
        assertFalse(result.isCompleted());
        assertFalse(result.isPending());

        verify(repo).saveAndFlush(any(IdempotencyKeyJpaEntity.class));
    }

    @Test
    void shouldReturnPendingStatusWhenKeyExistsAndPending() {
        IdempotencyKeyJpaEntity entity = new IdempotencyKeyJpaEntity("key-1", "PENDING", null, LocalDateTime.now());
        when(repo.findById("key-1")).thenReturn(Optional.of(entity));

        IdempotencyManager.IdempotencyResult result = manager.startRequest("key-1");

        assertEquals(IdempotencyManager.IdempotencyResult.Status.PENDING, result.status());
        assertTrue(result.isPending());
        assertFalse(result.isCompleted());
    }

    @Test
    void shouldReturnCompletedStatusWhenKeyExistsAndCompleted() {
        IdempotencyKeyJpaEntity entity = new IdempotencyKeyJpaEntity("key-1", "COMPLETED", "resp-body",
                LocalDateTime.now());
        when(repo.findById("key-1")).thenReturn(Optional.of(entity));

        IdempotencyManager.IdempotencyResult result = manager.startRequest("key-1");

        assertEquals(IdempotencyManager.IdempotencyResult.Status.COMPLETED, result.status());
        assertEquals("resp-body", result.responseBody());
        assertTrue(result.isCompleted());
        assertFalse(result.isPending());
    }

    @Test
    void shouldResolveConflictAsPendingWhenSaveFailsAndKeyIsPending() {
        IdempotencyKeyJpaEntity existing = new IdempotencyKeyJpaEntity("key-1", "PENDING", null, LocalDateTime.now());
        when(repo.findById("key-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        doThrow(new RuntimeException("Lock conflict")).when(repo).saveAndFlush(any());

        IdempotencyManager.IdempotencyResult result = manager.startRequest("key-1");

        assertEquals(IdempotencyManager.IdempotencyResult.Status.PENDING, result.status());
    }

    @Test
    void shouldResolveConflictAsCompletedWhenSaveFailsAndKeyIsCompleted() {
        IdempotencyKeyJpaEntity existing = new IdempotencyKeyJpaEntity("key-1", "COMPLETED", "resp",
                LocalDateTime.now());
        when(repo.findById("key-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        doThrow(new RuntimeException("Lock conflict")).when(repo).saveAndFlush(any());

        IdempotencyManager.IdempotencyResult result = manager.startRequest("key-1");

        assertEquals(IdempotencyManager.IdempotencyResult.Status.COMPLETED, result.status());
        assertEquals("resp", result.responseBody());
    }

    @Test
    void shouldThrowIllegalStateWhenConflictResolutionFailsToFindKey() {
        when(repo.findById("key-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        doThrow(new RuntimeException("Lock conflict")).when(repo).saveAndFlush(any());

        assertThrows(IllegalStateException.class, () -> manager.startRequest("key-1"));
    }

    @Test
    void shouldCompleteRequestAndUpdateEntity() {
        IdempotencyKeyJpaEntity entity = new IdempotencyKeyJpaEntity("key-1", "PENDING", null, LocalDateTime.now());
        when(repo.findById("key-1")).thenReturn(Optional.of(entity));

        manager.completeRequest("key-1", "success-response", 201);

        assertEquals("COMPLETED", entity.getStatus());
        assertEquals("success-response", entity.getResponseBody());
        assertEquals(201, entity.getResponseStatus());
        verify(repo).save(entity);
    }

    @Test
    void shouldDeleteKeyOnFailRequest() {
        manager.failRequest("key-1");

        verify(repo).deleteById("key-1");
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
        when(repo.findById("nonexistent")).thenReturn(Optional.empty());

        manager.completeRequest("nonexistent", "body", 200);

        verify(repo, never()).save(any());
    }
}
