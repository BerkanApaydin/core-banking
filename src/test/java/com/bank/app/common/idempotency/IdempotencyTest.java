package com.bank.app.common.idempotency;

import com.bank.app.common.persistence.IdempotencyKeyJpaEntity;
import com.bank.app.common.persistence.SpringDataIdempotencyKeyRepo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
class IdempotencyTest {

    @Test
    void testCleanupExpiredKeys() {
        SpringDataIdempotencyKeyRepo repo = mock(SpringDataIdempotencyKeyRepo.class);
        IdempotencyCleanupScheduler scheduler = new IdempotencyCleanupScheduler(repo, 24);

        when(repo.deleteByCreatedAtBefore(any(LocalDateTime.class))).thenReturn(5);

        scheduler.cleanupExpiredKeys();

        verify(repo).deleteByCreatedAtBefore(any(LocalDateTime.class));
    }

    @Test
    void testIdempotencyManager_NewRequest() {
        SpringDataIdempotencyKeyRepo repo = mock(SpringDataIdempotencyKeyRepo.class);
        IdempotencyManager manager = new IdempotencyManager(repo);

        when(repo.findById("key-1")).thenReturn(Optional.empty());

        IdempotencyManager.IdempotencyResult result = manager.startRequest("key-1");

        assertEquals(IdempotencyManager.IdempotencyResult.Status.NEW, result.status());
        assertNull(result.responseBody());
        assertFalse(result.isCompleted());
        assertFalse(result.isPending());

        verify(repo).saveAndFlush(any(IdempotencyKeyJpaEntity.class));
    }

    @Test
    void testIdempotencyManager_PendingRequest() {
        SpringDataIdempotencyKeyRepo repo = mock(SpringDataIdempotencyKeyRepo.class);
        IdempotencyManager manager = new IdempotencyManager(repo);

        IdempotencyKeyJpaEntity entity = new IdempotencyKeyJpaEntity("key-1", "PENDING", null, LocalDateTime.now());
        when(repo.findById("key-1")).thenReturn(Optional.of(entity));

        IdempotencyManager.IdempotencyResult result = manager.startRequest("key-1");

        assertEquals(IdempotencyManager.IdempotencyResult.Status.PENDING, result.status());
        assertTrue(result.isPending());
        assertFalse(result.isCompleted());
    }

    @Test
    void testIdempotencyManager_CompletedRequest() {
        SpringDataIdempotencyKeyRepo repo = mock(SpringDataIdempotencyKeyRepo.class);
        IdempotencyManager manager = new IdempotencyManager(repo);

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
    void testIdempotencyManager_ConflictResolutionPending() {
        SpringDataIdempotencyKeyRepo repo = mock(SpringDataIdempotencyKeyRepo.class);
        IdempotencyManager manager = new IdempotencyManager(repo);

        IdempotencyKeyJpaEntity existing = new IdempotencyKeyJpaEntity("key-1", "PENDING", null, LocalDateTime.now());
        when(repo.findById("key-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        doThrow(new RuntimeException("Lock conflict")).when(repo).saveAndFlush(any());

        IdempotencyManager.IdempotencyResult result = manager.startRequest("key-1");

        assertEquals(IdempotencyManager.IdempotencyResult.Status.PENDING, result.status());
    }

    @Test
    void testIdempotencyManager_ConflictResolutionCompleted() {
        SpringDataIdempotencyKeyRepo repo = mock(SpringDataIdempotencyKeyRepo.class);
        IdempotencyManager manager = new IdempotencyManager(repo);

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
    void testIdempotencyManager_ConflictResolutionFailedToFind() {
        SpringDataIdempotencyKeyRepo repo = mock(SpringDataIdempotencyKeyRepo.class);
        IdempotencyManager manager = new IdempotencyManager(repo);

        when(repo.findById("key-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        doThrow(new RuntimeException("Lock conflict")).when(repo).saveAndFlush(any());

        assertThrows(IllegalStateException.class, () -> manager.startRequest("key-1"));
    }

    @Test
    void testIdempotencyManager_CompleteRequest() {
        SpringDataIdempotencyKeyRepo repo = mock(SpringDataIdempotencyKeyRepo.class);
        IdempotencyManager manager = new IdempotencyManager(repo);

        IdempotencyKeyJpaEntity entity = new IdempotencyKeyJpaEntity("key-1", "PENDING", null, LocalDateTime.now());
        when(repo.findById("key-1")).thenReturn(Optional.of(entity));

        manager.completeRequest("key-1", "success-response");

        assertEquals("COMPLETED", entity.getStatus());
        assertEquals("success-response", entity.getResponseBody());
        verify(repo).save(entity);
    }

    @Test
    void testIdempotencyManager_FailRequest() {
        SpringDataIdempotencyKeyRepo repo = mock(SpringDataIdempotencyKeyRepo.class);
        IdempotencyManager manager = new IdempotencyManager(repo);

        manager.failRequest("key-1");

        verify(repo).deleteById("key-1");
    }
}
