package com.bank.app.audit.adapter.out.persistence;

import com.bank.app.audit.domain.AuditAction;
import com.bank.app.audit.domain.AuditLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditPersistenceAdapterTest {

    @Mock
    private AuditLogJpaRepository springDataRepo;

    private AuditLogPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AuditLogPersistenceAdapter(springDataRepo, new AuditLogJpaMapper());
    }

    @Test
    void shouldSaveAuditLogSuccessfully() {
        LocalDateTime timestamp = LocalDateTime.now();
        AuditLog domainLog = new AuditLog(null, "user123", AuditAction.TRANSFER_EXECUTED, "Details here", timestamp);
        AuditLogJpaEntity savedEntity = new AuditLogJpaEntity(1L, "user123", AuditAction.TRANSFER_EXECUTED, "Details here", timestamp);

        when(springDataRepo.save(any())).thenReturn(savedEntity);

        AuditLog result = adapter.save(domainLog);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("user123", result.getUsername());
        assertEquals(AuditAction.TRANSFER_EXECUTED, result.getAction());
        assertEquals("Details here", result.getDetails());
        assertEquals(timestamp, result.getTimestamp());

        ArgumentCaptor<AuditLogJpaEntity> captor = ArgumentCaptor.forClass(AuditLogJpaEntity.class);
        verify(springDataRepo).save(captor.capture());

        AuditLogJpaEntity captured = captor.getValue();
        assertEquals("user123", captured.getUsername());
        assertEquals(AuditAction.TRANSFER_EXECUTED, captured.getAction());
        assertEquals("Details here", captured.getDetails());
        assertEquals(timestamp, captured.getTimestamp());
    }

    @Test
    void shouldFindByActorWithCappedPage() {
        LocalDateTime timestamp = LocalDateTime.now();
        AuditLogJpaEntity entity = new AuditLogJpaEntity(2L, "alice", AuditAction.LOGIN_SUCCEEDED, "ok", timestamp);
        when(springDataRepo.findByActorUserIdOrderByTimestampDescIdDesc(eq(7L), any()))
                .thenReturn(List.of(entity));

        List<AuditLog> result = adapter.findByActor(7L, 10);

        assertEquals(1, result.size());
        assertEquals("alice", result.get(0).getUsername());
        verify(springDataRepo).findByActorUserIdOrderByTimestampDescIdDesc(eq(7L), any());
    }

    @Test
    void shouldFindByTimeRange() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();
        AuditLogJpaEntity entity = new AuditLogJpaEntity(3L, "bob", AuditAction.ACCOUNT_CREATED, "opened", to);
        when(springDataRepo.findByTimestampBetweenOrderByTimestampDescIdDesc(eq(from), eq(to), any()))
                .thenReturn(List.of(entity));

        List<AuditLog> result = adapter.findByTimeRange(from, to, 10);

        assertEquals(1, result.size());
        assertEquals(AuditAction.ACCOUNT_CREATED, result.get(0).getAction());
    }

    @Test
    void shouldDeleteOlderThanCutoff() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(365);
        when(springDataRepo.deleteByTimestampBefore(cutoff)).thenReturn(12);

        assertEquals(12, adapter.deleteOlderThan(cutoff));
    }

    @Test
    void shouldCapOversizedPagesAtAdapterBoundary() {
        when(springDataRepo.findAllByOrderByTimestampDescIdDesc(any()))
                .thenReturn(List.of());

        adapter.findRecent(10_000);

        ArgumentCaptor<Pageable> pageCaptor =
                ArgumentCaptor.forClass(Pageable.class);
        verify(springDataRepo).findAllByOrderByTimestampDescIdDesc(pageCaptor.capture());
        assertEquals(500, pageCaptor.getValue().getPageSize());
    }

    @Test
    void shouldMapRecentLogsInsteadOfReturningEmpty() {
        // Kills the EmptyObjectReturn mutant on findRecent: the mutant would
        // return an empty list even when the repository has rows.
        LocalDateTime timestamp = LocalDateTime.now();
        AuditLogJpaEntity entity = new AuditLogJpaEntity(9L, "carol",
                AuditAction.TRANSFER_EXECUTED, "Transfer completed. Transfer ID: 3", timestamp);
        when(springDataRepo.findAllByOrderByTimestampDescIdDesc(any()))
                .thenReturn(List.of(entity));

        List<AuditLog> result = adapter.findRecent(10);

        assertEquals(1, result.size());
        assertEquals("carol", result.get(0).getUsername());
        assertEquals(AuditAction.TRANSFER_EXECUTED, result.get(0).getAction());
    }
}
